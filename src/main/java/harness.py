# executes Harness.java
# manages source file input and executed output

import argparse
import os
import subprocess

COMPILE = "javac"
HARNESS = "Harness"
RUNTIME = "java"

# place this file in the same directory as:
#   -> your plc directory
#   -> Harness.java
#   -> source1.plc
#   -> source2.plc

# to execute from end-to-end
#  -> run python on harness.py and pass in a source file

# for example, at the terminal enter:
#  python (or python3) harness.py source1.plc

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Execute Complete PLC Generator")

    parser.add_argument("source")
    args = parser.parse_args()

    source_file = args.source
    java_file = "Main.java"
    class_file = "Main"

    print("\nRunning the test harness on ->", source_file, "\n")

    try:
        subprocess.run([RUNTIME, HARNESS, source_file])

        if os.path.exists(java_file):
            print("Generated: ", java_file, "\n")
            subprocess.run([COMPILE, java_file])

            if os.path.exists(class_file + ".class"):
                print("Compiled: ", class_file, "\n")
                print("OUTPUT\n")
                subprocess.call([RUNTIME, class_file])

    except KeyboardInterrupt:
        print("\nExiting Harness")

    print()
