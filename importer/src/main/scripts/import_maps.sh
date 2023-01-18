#!/bin/bash

ROOT_DIR=`dirname $0`
echo `date`
export OSM_DB_PATH=$HOME/Maps/osm/db
export OSM_MAPS_PATH=$HOME/Downloads/geofabrik
export OSM_IMPORT_PROGRESS=0
rm $OSM_DB_PATH/*.db > /dev/null 2>&1
$ROOT_DIR/importer-1.0-SNAPSHOT/bin/importer > /dev/null 2>&1
echo `date`
