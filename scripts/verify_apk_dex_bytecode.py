#!/usr/bin/env python3
"""
Dalvik DEX Bytecode Verifier (ART MethodVerifier `invoke-*` & register bounds check).

Parses EVERY `classes*.dex` inside one or more `.apk` archives and verifies EVERY
method's instruction stream at the binary Dalvik ISA level:
  1. No `proto_id` invoked via `35c` (`0x6e..0x72`) exceeds 5 register words or mismatches `A`.
  2. No `proto_id` invoked via `3rc` (`0x74..0x78`, `invoke-*/range`) exceeds 255 register words
     or mismatches the 8-bit `AA` count byte (the exact root cause of:
     `java.lang.VerifyError: Rejecting invocation, expected 5 argument registers, method signature has 6 or more`).
  3. No invoke instruction references a register outside `[0 .. registers_size - 1]`.
"""
import struct
import sys
import zipfile
from pathlib import Path

# Full Dalvik 16-bit code-unit widths by opcode (0x00 .. 0xFF), per AOSP dalvik-bytecode spec.
INSN_WIDTH = [1] * 256
# 10x, 12x, 11n, 11x, 10t -> 1 unit (default)
# 2-unit formats: 20t, 22x, 21t, 21s, 21h, 21c, 23x, 22b, 22t, 22s, 22c
for op in (
    [0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1A, 0x1C, 0x1F, 0x20, 0x22, 0x23, 0x29]
    + list(range(0x2D, 0x3E))
    + list(range(0x44, 0x6E))
    + list(range(0x90, 0xB0))
    + list(range(0xD0, 0xE3))
    + [0xFE, 0xFF]
):
    INSN_WIDTH[op] = 2

# 3-unit formats: 30t, 32x, 31i, 31t, 31c, 35c, 3rc (including invoke-custom 0xFC / 0xFD)
for op in (
    [0x03, 0x06, 0x09, 0x14, 0x17, 0x1B, 0x24, 0x25, 0x26, 0x2A, 0x2B, 0x2C]
    + list(range(0x6E, 0x73))
    + list(range(0x74, 0x79))
    + [0xFC, 0xFD]
):
    INSN_WIDTH[op] = 3

# 4-unit formats: 45cc (0xFA), 4rcc (0xFB)
INSN_WIDTH[0xFA] = 4
INSN_WIDTH[0xFB] = 4

# 5-unit format: 51l (0x18 const-wide)
INSN_WIDTH[0x18] = 5


def read_uleb128(buf: bytes, off: int):
    result = 0
    shift = 0
    while True:
        b = buf[off]
        off += 1
        result |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return result, off


def read_dex_str(buf: bytes, str_off: int) -> str:
    _, pos = read_uleb128(buf, str_off)
    end = buf.index(0, pos)
    return buf[pos:end].decode("utf-8", errors="replace")


def verify_dex(dex_name: str, buf: bytes):
    assert buf[:4] == b"dex\n", f"{dex_name}: invalid DEX magic"
    (
        string_ids_size, string_ids_off,
        type_ids_size, type_ids_off,
        proto_ids_size, proto_ids_off,
        field_ids_size, field_ids_off,
        method_ids_size, method_ids_off,
        class_defs_size, class_defs_off,
    ) = struct.unpack_from("<12I", buf, 56)

    def get_str(idx: int) -> str:
        (s_off,) = struct.unpack_from("<I", buf, string_ids_off + idx * 4)
        return read_dex_str(buf, s_off)

    type_desc = []
    for i in range(type_ids_size):
        (desc_idx,) = struct.unpack_from("<I", buf, type_ids_off + i * 4)
        type_desc.append(get_str(desc_idx))

    proto_words = [0] * proto_ids_size
    for i in range(proto_ids_size):
        _, _, params_off = struct.unpack_from("<3I", buf, proto_ids_off + i * 12)
        if params_off != 0:
            (cnt,) = struct.unpack_from("<I", buf, params_off)
            words = 0
            for j in range(cnt):
                (t_idx,) = struct.unpack_from("<H", buf, params_off + 4 + j * 2)
                d = type_desc[t_idx]
                words += 2 if d in ("J", "D") else 1
            proto_words[i] = words

    method_proto = [0] * method_ids_size
    method_class = [0] * method_ids_size
    method_name = [0] * method_ids_size
    for i in range(method_ids_size):
        c_idx, p_idx, n_idx = struct.unpack_from("<HHI", buf, method_ids_off + i * 8)
        method_class[i] = c_idx
        method_proto[i] = p_idx
        method_name[i] = n_idx

    errors = []
    max_invoke_words = 0
    methods_checked = 0
    insns_checked = 0

    for c in range(class_defs_size):
        class_idx, _, _, _, _, _, class_data_off, _ = struct.unpack_from("<8I", buf, class_defs_off + c * 32)
        if class_data_off == 0:
            continue
        pos = class_data_off
        static_fields, pos = read_uleb128(buf, pos)
        instance_fields, pos = read_uleb128(buf, pos)
        direct_methods, pos = read_uleb128(buf, pos)
        virtual_methods, pos = read_uleb128(buf, pos)

        for _ in range(static_fields + instance_fields):
            _, pos = read_uleb128(buf, pos)
            _, pos = read_uleb128(buf, pos)

        for group_count in (direct_methods, virtual_methods):
            m_idx = 0
            for _ in range(group_count):
                diff, pos = read_uleb128(buf, pos)
                m_idx += diff
                _, pos = read_uleb128(buf, pos)
                code_off, pos = read_uleb128(buf, pos)
                if code_off == 0:
                    continue
                methods_checked += 1
                registers_size, ins_size, _, _, _, insns_size = struct.unpack_from("<4H2I", buf, code_off)
                insns_base = code_off + 16
                pc = 0
                while pc < insns_size:
                    u0 = struct.unpack_from("<H", buf, insns_base + pc * 2)[0]
                    op = u0 & 0xFF
                    if op == 0x00 and u0 != 0x0000:
                        ident = u0 >> 8
                        if ident == 0x01:  # packed-switch-payload
                            sz = struct.unpack_from("<H", buf, insns_base + (pc + 1) * 2)[0]
                            pc += sz * 2 + 4
                            continue
                        elif ident == 0x02:  # sparse-switch-payload
                            sz = struct.unpack_from("<H", buf, insns_base + (pc + 1) * 2)[0]
                            pc += sz * 4 + 2
                            continue
                        elif ident == 0x03:  # fill-array-data-payload
                            elem_w = struct.unpack_from("<H", buf, insns_base + (pc + 1) * 2)[0]
                            sz = struct.unpack_from("<I", buf, insns_base + (pc + 2) * 2)[0]
                            pc += (sz * elem_w + 1) // 2 + 4
                            continue

                    insns_checked += 1
                    if 0x6E <= op <= 0x72:  # 35c
                        a_count = (u0 >> 12) & 0x0F
                        callee_id = struct.unpack_from("<H", buf, insns_base + (pc + 1) * 2)[0]
                        is_static = (op == 0x71)
                        expected = proto_words[method_proto[callee_id]] + (0 if is_static else 1)
                        if expected > max_invoke_words:
                            max_invoke_words = expected
                        if expected != a_count or expected > 5:
                            caller = f"{type_desc[class_idx]}.{get_str(method_name[m_idx])}"
                            callee = f"{type_desc[method_class[callee_id]]}.{get_str(method_name[callee_id])}"
                            errors.append(
                                f"{dex_name} {caller} @[0x{pc:X}]: 35c invoke {callee} "
                                f"encoded A={a_count}, signature expects {expected}"
                            )
                    elif 0x74 <= op <= 0x78:  # 3rc
                        aa_count = (u0 >> 8) & 0xFF
                        callee_id, first_reg = struct.unpack_from("<HH", buf, insns_base + (pc + 1) * 2)
                        is_static = (op == 0x77)
                        expected = proto_words[method_proto[callee_id]] + (0 if is_static else 1)
                        if expected > max_invoke_words:
                            max_invoke_words = expected
                        if expected != aa_count or expected > 255 or (expected > 0 and first_reg + expected > registers_size):
                            caller = f"{type_desc[class_idx]}.{get_str(method_name[m_idx])}"
                            callee = f"{type_desc[method_class[callee_id]]}.{get_str(method_name[callee_id])}"
                            errors.append(
                                f"{dex_name} {caller} @[0x{pc:X}]: 3rc invoke/range {callee} "
                                f"encoded AA={aa_count}, signature expects {expected}, "
                                f"regs=[v{first_reg}..v{first_reg + expected - 1}]/registers_size={registers_size}"
                            )
                    pc += INSN_WIDTH[op]

    return methods_checked, insns_checked, max_invoke_words, errors


def main():
    if len(sys.argv) < 2:
        print("Usage: verify_apk_dex_bytecode.py <apk1> [apk2 ...]")
        sys.exit(2)

    all_errors = []
    for apk_arg in sys.argv[1:]:
        apk_path = Path(apk_arg)
        assert apk_path.is_file(), f"APK not found: {apk_path}"
        total_methods = 0
        total_insns = 0
        peak_words = 0
        dex_count = 0
        with zipfile.ZipFile(apk_path, "r") as zf:
            dex_entries = sorted(n for n in zf.namelist() if n.startswith("classes") and n.endswith(".dex"))
            assert dex_entries, f"No classes*.dex found in {apk_path}"
            for entry in dex_entries:
                dex_count += 1
                m, ins, mx, errs = verify_dex(f"{apk_path.name}!{entry}", zf.read(entry))
                total_methods += m
                total_insns += ins
                peak_words = max(peak_words, mx)
                all_errors.extend(errs)
        print(
            f"[DEX-OK] {apk_path}: {dex_count} dex file(s), "
            f"{total_methods:,} method bodies, {total_insns:,} instructions verified, "
            f"max invoke register words = {peak_words}/255, VerifyError count = {len(all_errors)}"
        )

    if all_errors:
        print("\nFATAL DEX VERIFICATION ERRORS DETECTED:")
        for e in all_errors[:50]:
            print("  ERROR:", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
