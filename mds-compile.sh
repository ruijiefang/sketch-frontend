#!/bin/bash
make light-distr
cp ./mdsSketch ../sketch-1.7.6/sketch-frontend
#cp ~/Projects/cos516project/helloworld.sk ../sketch-1.7.6/sketch-frontend 
cp mds-tests/*.sk ../sketch-1.7.6/sketch-frontend
cp -r mds-tests/ ../sketch-1.7.6/sketch-frontend
echo " Successfully compiled everything!"
