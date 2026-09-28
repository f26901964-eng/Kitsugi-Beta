import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

client_log_path = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\logs\latest.log"
if os.path.exists(client_log_path):
    print("Reading log file...")
    with open(client_log_path, 'r', encoding='utf-8', errors='replace') as f:
        log_content = f.read()
    
    # Find lines containing DistantHorizons errors around 'Unable to load level'
    lines = log_content.splitlines()
    error_indices = [i for i, l in enumerate(lines) if "Unable to load level" in l or "Client level loading failed" in l]
    
    if error_indices:
        print(f"Found {len(error_indices)} occurrences of the error. Details from the first occurrence:")
        first_idx = error_indices[0]
        # print 30 lines before and 30 lines after the error to see the full stack trace
        start = max(0, first_idx - 15)
        end = min(len(lines), first_idx + 40)
        for i in range(start, end):
            print(f"{i+1}: {lines[i]}")
    else:
        print("Could not find the exact error message in latest.log")
        # Let's search for general SQLITE or DH errors
        sqlite_lines = [l for l in lines if "sqlite" in l.lower() or "distanthorizons" in l.lower() and ("error" in l.lower() or "exception" in l.lower() or "failed" in l.lower())]
        print(f"Found {len(sqlite_lines)} general DH/SQLite errors. Last 10:")
        for l in sqlite_lines[-10:]:
            print(l)
else:
    print("Client latest.log not found!")
