#!/bin/bash

pushd ..

sbt clean compile assembly

rm XTL.tar XTL.tar.gz
cp target/*/plus500-assembly-0.1-SNAPSHOT.jar .
tar -cvf XTL.tar plus500-assembly-0.1-SNAPSHOT.jar data/symbols.txt
gzip XTL.tar

scp XTL.tar.gz amazon:~/

ssh amazon '
    mkdir plus500
    cp XTL.tar.gz plus500
    cd plus500
    tar -xvf XTL.tar.gz
    screen -d -m java -cp plus500-assembly-0.1-SNAPSHOT.jar main.scala.im.boddy.plus500.scraper.XTL plus500.db
'

rm XTL.tar XTL.tar.gz
popd


