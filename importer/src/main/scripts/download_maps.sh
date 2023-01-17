#!/bin/bash

OSM_MAPS_PATH=$HOME/Downloads/geofabrik
MAP_FILES[0]="https://download.geofabrik.de/europe/austria-latest.osm.pbf"
MAP_FILES[1]="https://download.geofabrik.de/europe/liechtenstein-latest.osm.pbf"
MAP_FILES[2]="https://download.geofabrik.de/europe/germany/bayern-latest.osm.pbf"
MAP_FILES[3]="https://download.geofabrik.de/europe/switzerland-latest.osm.pbf"

for MAP_FILE in ${MAP_FILES[*]}
do
    curl $MAP_FILE --output $OSM_MAPS_PATH/`basename $MAP_FILE`
done