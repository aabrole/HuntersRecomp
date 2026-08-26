cmake_minimum_required(VERSION 3.20)

project(ThorMPH)

# SDL sources are symlinked in a subfolder named "SDL" -> the SDL2 checkout.
add_subdirectory(SDL/SDL2 ${CMAKE_BINARY_DIR}/SDL2-build)

# Options the ndsrecomp runner needs for the Metroid Prime Hunters (AMHE0)
# title-bank Android build. Set before add_subdirectory so the runner picks
# them up as normal (non-cache) variables.
set(NDS_BOOTSTRAP_FIRMWARE ON CACHE BOOL "" FORCE)
set(NDS_ENABLE_COMPUTE_RENDERER OFF CACHE BOOL "" FORCE)
set(NDS_TITLE_BANK_DIR
    "/Users/ameezy/MetroidPrimeHuntersRecomp/generated/recomp"
    CACHE PATH "" FORCE)
set(NDS_TITLE_ROM_SHA1
    "90164d1ac127ee5f9815ea4ae7de798c7b5fc629" CACHE STRING "" FORCE)

# The runner builds as libmain.so on Android (ANDROID is set by the NDK
# toolchain), which SDLActivity loads as the "main" library.
add_subdirectory(/Users/ameezy/ndsrecomp/runner
                 ${CMAKE_BINARY_DIR}/ndsrunner-build)
