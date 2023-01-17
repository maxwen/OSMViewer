#!/bin/sh
rm import.log
date > import.log
importer-1.0-SNAPSHOT/bin/importer >> import.log 2>&1
date >> import.log
