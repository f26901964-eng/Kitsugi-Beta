import re

with open(r'C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\logs\debug.log', 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()

print(f"Total lines: {len(lines)}")

# Find lines containing "Game crashed!" or the Sodium config exception
crash_indices = []
for idx, line in enumerate(lines):
    if 'Game crashed!' in line or 'Sodium' in line and 'config' in line and 'could not be found' in line:
        crash_indices.append(idx)

print(f"Found crash indices: {crash_indices}")

with open('debug_crash_analysis.txt', 'w', encoding='utf-8') as out:
    for c_idx in crash_indices:
        out.write(f"\n=========================================\n")
        out.write(f"CRASH AT LINE {c_idx}: {lines[c_idx].strip()}\n")
        out.write(f"=========================================\n")
        
        # Print 120 lines before the crash
        start = max(0, c_idx - 120)
        end = min(len(lines), c_idx + 10)
        for i in range(start, end):
            out.write(f"{i}: {lines[i]}")
