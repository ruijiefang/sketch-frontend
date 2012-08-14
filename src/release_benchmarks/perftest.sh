
RESDIR=$1
echo "Creating Results Directory " $RESDIR
mkdir $RESDIR



for ii in {1..9}
do

for x in gallery/*.sk performance_benchmarks/*/*.sk
do
echo "Running" $x
BNAME=${x/*\//}
bash sketch -V 4  --slv-seed $ii  --fe-inc '..\sketchlib' --slv-timeout 15  $x > $RESDIR/$BNAME.$ii 2>&1

done

done 