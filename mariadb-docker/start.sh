#!/bin/bash

BRANCH=$1
NPROC=`nproc --all`
PREFIX=~/mariadb-bin

LEIN_OPTIONS="run test --db maria-docker --nodes localhost --concurrency ${NPROC} --rate 1000 --time-limit 60 --key-count 40 --no-ssh=true --innodb-strict-isolation=true"

cd ~
# Update the test
rm -rf jepsen-mysql
git clone https://github.com/vlad-lesin/jepsen-mysql
git clone https://github.com/MariaDB/server --branch $BRANCH --depth 1

mkdir build
cd build
cmake ../server -DPLUGIN_{ARCHIVE,TOKUDB,MROONGA,OQGRAPH,ROCKSDB,CONNECT,SPIDER,SPHINX,COLUMNSTORE,PERFSCHEMA,XPAND}=NO -DWITH_SAFEMALLOC=OFF -DCMAKE_BUILD_TYPE=RelWithDebinfo -DCMAKE_INSTALL_PREFIX=$PREFIX && make -j$NPROC install

# SSH is not neccessary for single-machine tests, but Jepsen itself can
# require it for some cases, so just leave it here just in case.
# sudo service ssh start

cd ../jepsen-mysql
echo "===========Append serializable============="
../lein $LEIN_OPTIONS -w append -i serializable
echo "===========Append repeatable-read============="
../lein $LEIN_OPTIONS -w append -i repeatable-read
echo "===========Append read-committed============="
../lein $LEIN_OPTIONS -w append -i read-committed
echo "===========Append read-uncommitted============="
../lein $LEIN_OPTIONS -w append -i read-uncommitted

echo "===========Non-repeatable read serializable============="
../lein $LEIN_OPTIONS -w nonrepeatable-read -i serializable
echo "===========Non-repeatable repeatable-read============="
../lein $LEIN_OPTIONS -w nonrepeatable-read -i repeatable-read

echo "===========mav serializable============="
../lein $LEIN_OPTIONS -w mav -i serializable
echo "===========mav repeatable-read============="
../lein $LEIN_OPTIONS -w mav -i repeatable-read

