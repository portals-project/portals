# # Mac Hardware
# system_profiler SPSoftwareDataType SPHardwareDataType > hardware.txt 2>&1

# # SBT and Java Options
# sbt -v exit > options.txt 2>&1

# Run benchmark
time sbt run > output.txt

# Extract results
cat output.txt | grep name > results.txt

# Print results (for convenience)
cat results.txt