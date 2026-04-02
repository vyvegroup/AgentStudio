cmake_minimum_required(VERSION 3.22.1)
project("venca")

# VenCA Security Native Library
add_library(
    venca_security
    SHARED
    venca_security.cpp
)

# Compiler flags for security
target_compile_options(venca_security PRIVATE
    -O3                    # Optimize for performance
    -fvisibility=hidden    # Hide symbols by default
    -ffunction-sections    # Put each function in its own section
    -fdata-sections        # Put each data in its own section
    -fstack-protector-all  # Stack protection
    -D_FORTIFY_SOURCE=2    # Buffer overflow protection
)

# Linker flags for security
target_link_options(venca_security PRIVATE
    -Wl,--gc-sections      # Garbage collect unused sections
    -Wl,--strip-all        # Strip all symbols
    -Wl,--as-needed        # Only link needed libraries
)

# Link required libraries
target_link_libraries(venca_security
    log                    # Android logging
    android                # Android native API
)

# Find required packages
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(venca_security
    ${log-lib}
    ${android-lib}
)
