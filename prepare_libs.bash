#!/bin/bash

# This script is used to prepare the libraries for the build process.
# It builds them and copies the necessary files to the correct locations.
# The script must be run from the root of the project.

CURRENT_DIR=$PWD

# Build ompi
cd 3rd-party/ompi &&
  echo "Building Open MPI" &&
  ./autogen.pl &&
  echo "Configuring Open MPI" &&
  ./configure \
    --prefix="$CURRENT_DIR"/libs/ompi \
    --enable-mpi-java \
    --with-libevent=external \
    --with-hwloc=external \
    --with-pmix=internal \
    --with-prrte=internal &&
  echo "Building Open MPI" &&
  make -j 4 all &&
  echo "Installing Open MPI" &&
  make install &&
  echo "Open MPI built and installed"
