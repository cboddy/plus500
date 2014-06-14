#!/bin/bash

pushd ..

make
rm XTL.tar XTL.tar.gz

tar -cvf XTL.tar XTL.jar lib/ data/symbols.txt
gzip XTL.tar


scp XTL.tar.gz amazon:~/

ssh amazon '
    mkdir plus500
    mv XTL.tar.gz plus500
    cd plus500
    tar -xvf XTL.tar.gz
    screen -D -m java -jar XTL.jar plus500.db
'
popd


