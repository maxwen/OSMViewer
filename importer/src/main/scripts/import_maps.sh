#!/bin/bash

ROOT_DIR=`dirname $0`
echo `date`
export OSM_DB_PATH=$HOME/Maps/osm/db
export OSM_MAPS_PATH=$HOME/Downloads/geofabrik
export OSM_IMPORT_PROGRESS=0
export OSM_IMPORT_ALL=1
export LD_LIBRARY_PATH=/home/maxwen/osm/libspatialite-5.0.1/src/.libs:$LD_LIBRARY_PATH

rm $OSM_DB_PATH/*.db > /dev/null 2>&1
$ROOT_DIR/importer-1.0-SNAPSHOT/bin/importer
echo `date`
