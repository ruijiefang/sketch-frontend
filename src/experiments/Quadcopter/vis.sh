g++ -I "$SKETCH_HOME/include" -o simpleCopterTest simpleCopterTest.cpp simpleCopterTest_test.cpp

echo 'var lastang = 0;'

echo 'function moveCopter(cxb,cyb, ang){'
echo '  var ctrx = cxb;'
echo '  var ctry = cyb;'
echo '  ang = -ang;'
echo '  moveTo("copter", ctrx, ctry , 1);'
echo '  turn("copter", {x:(ang-lastang)*3});'
echo '  lastang = ang;'
echo '}'
echo 'draw("copter", 10, 50-15, 0.4);' 
./simpleCopterTest -n 1 | awk '{print "moveCopter("$1", 50-("$2"), "$3");"}'

