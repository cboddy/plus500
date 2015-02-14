plus500
=======

A Scala eXtraction, Transform, Load (XTL)  pipeline for plus500 financial instrument price data. 

Periodically gets price data from plus500.co.uk for a range of financial instruments and stores the prices in an sqlite database. 

SQLite could be switched for MariaDB / MySQL etc. almost for free. The list of financial instrument prices to poll are in data/symbols.txt which can easily be edited. The complete list of available instruments is in data/all.symbols.txt.

To build, you can use sbt-assemby for an all-in-one jar

> sbt clean compile assembly 


The deployment script deploy/deploy.sh is particular to my AWS instance (more precisely, my ssh config 'amazon' entry). If you want to use this script you'll have to edit your ssh config appropriately.
