#include <cstdio>
#include <assert.h>
#include "xpose.h"
void sse_transpose(fixedarr<int, 16>  mx_0, fixedarr<int, 16> & s_1) {
  fixedarr<int, 16> p0_2 = 0;
  fixedarr<int, 16> p1_3 = 0;
  fixedarr<int, 4> s_4 = mx_0.sub<4>(12);
  fixedarr<int, 4> s_5 = mx_0.sub<4>(8);
  bitvec<8> s_6 = "01000001";
  fixedarr<int, 4> ret_7 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_8 = s_6.sub<2>(0);
  ret_7[0] = s_4.sub<1>(((int)(s_8)));
  bitvec<2> s_9 = s_6.sub<2>(2);
  ret_7[1] = s_4.sub<1>(((int)(s_9)));
  bitvec<2> s_10 = s_6.sub<2>(4);
  ret_7[2] = s_5.sub<1>(((int)(s_10)));
  bitvec<2> s_11 = s_6.sub<2>(6);
  ret_7[3] = s_5.sub<1>(((int)(s_11)));
  p0_2[0] = ret_7;
  fixedarr<int, 4> s_12 = mx_0.sub<4>(12);
  fixedarr<int, 4> s_13 = mx_0.sub<4>(8);
  bitvec<8> s_14 = "11101011";
  fixedarr<int, 4> ret_15 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_16 = s_14.sub<2>(0);
  ret_15[0] = s_12.sub<1>(((int)(s_16)));
  bitvec<2> s_17 = s_14.sub<2>(2);
  ret_15[1] = s_12.sub<1>(((int)(s_17)));
  bitvec<2> s_18 = s_14.sub<2>(4);
  ret_15[2] = s_13.sub<1>(((int)(s_18)));
  bitvec<2> s_19 = s_14.sub<2>(6);
  ret_15[3] = s_13.sub<1>(((int)(s_19)));
  p0_2[8] = ret_15;
  fixedarr<int, 4> s_20 = mx_0.sub<4>(0);
  fixedarr<int, 4> s_21 = mx_0.sub<4>(4);
  bitvec<8> s_22 = "00010100";
  fixedarr<int, 4> ret_23 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_24 = s_22.sub<2>(0);
  ret_23[0] = s_20.sub<1>(((int)(s_24)));
  bitvec<2> s_25 = s_22.sub<2>(2);
  ret_23[1] = s_20.sub<1>(((int)(s_25)));
  bitvec<2> s_26 = s_22.sub<2>(4);
  ret_23[2] = s_21.sub<1>(((int)(s_26)));
  bitvec<2> s_27 = s_22.sub<2>(6);
  ret_23[3] = s_21.sub<1>(((int)(s_27)));
  p0_2[4] = ret_23;
  fixedarr<int, 4> s_28 = mx_0.sub<4>(4);
  fixedarr<int, 4> s_29 = mx_0.sub<4>(0);
  bitvec<8> s_30 = "11101011";
  fixedarr<int, 4> ret_31 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_32 = s_30.sub<2>(0);
  ret_31[0] = s_28.sub<1>(((int)(s_32)));
  bitvec<2> s_33 = s_30.sub<2>(2);
  ret_31[1] = s_28.sub<1>(((int)(s_33)));
  bitvec<2> s_34 = s_30.sub<2>(4);
  ret_31[2] = s_29.sub<1>(((int)(s_34)));
  bitvec<2> s_35 = s_30.sub<2>(6);
  ret_31[3] = s_29.sub<1>(((int)(s_35)));
  p0_2[12] = ret_31;
  fixedarr<int, 4> s_36 = p0_2.sub<4>(12);
  fixedarr<int, 4> s_37 = p0_2.sub<4>(8);
  bitvec<8> s_38 = "11001100";
  fixedarr<int, 4> ret_39 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_40 = s_38.sub<2>(0);
  ret_39[0] = s_36.sub<1>(((int)(s_40)));
  bitvec<2> s_41 = s_38.sub<2>(2);
  ret_39[1] = s_36.sub<1>(((int)(s_41)));
  bitvec<2> s_42 = s_38.sub<2>(4);
  ret_39[2] = s_37.sub<1>(((int)(s_42)));
  bitvec<2> s_43 = s_38.sub<2>(6);
  ret_39[3] = s_37.sub<1>(((int)(s_43)));
  p1_3[12] = ret_39;
  fixedarr<int, 4> s_44 = p0_2.sub<4>(4);
  fixedarr<int, 4> s_45 = p0_2.sub<4>(0);
  bitvec<8> s_46 = "00110110";
  fixedarr<int, 4> ret_47 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_48 = s_46.sub<2>(0);
  ret_47[0] = s_44.sub<1>(((int)(s_48)));
  bitvec<2> s_49 = s_46.sub<2>(2);
  ret_47[1] = s_44.sub<1>(((int)(s_49)));
  bitvec<2> s_50 = s_46.sub<2>(4);
  ret_47[2] = s_45.sub<1>(((int)(s_50)));
  bitvec<2> s_51 = s_46.sub<2>(6);
  ret_47[3] = s_45.sub<1>(((int)(s_51)));
  p1_3[0] = ret_47;
  fixedarr<int, 4> s_52 = p0_2.sub<4>(4);
  fixedarr<int, 4> s_53 = p0_2.sub<4>(0);
  bitvec<8> s_54 = "10011100";
  fixedarr<int, 4> ret_55 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_56 = s_54.sub<2>(0);
  ret_55[0] = s_52.sub<1>(((int)(s_56)));
  bitvec<2> s_57 = s_54.sub<2>(2);
  ret_55[1] = s_52.sub<1>(((int)(s_57)));
  bitvec<2> s_58 = s_54.sub<2>(4);
  ret_55[2] = s_53.sub<1>(((int)(s_58)));
  bitvec<2> s_59 = s_54.sub<2>(6);
  ret_55[3] = s_53.sub<1>(((int)(s_59)));
  p1_3[8] = ret_55;
  fixedarr<int, 4> s_60 = p0_2.sub<4>(12);
  fixedarr<int, 4> s_61 = p0_2.sub<4>(8);
  bitvec<8> s_62 = "01100110";
  fixedarr<int, 4> ret_63 = fixedarr< int,4>().v(0,0).v(1,0).v(2,0).v(3,0);
  bitvec<2> s_64 = s_62.sub<2>(0);
  ret_63[0] = s_60.sub<1>(((int)(s_64)));
  bitvec<2> s_65 = s_62.sub<2>(2);
  ret_63[1] = s_60.sub<1>(((int)(s_65)));
  bitvec<2> s_66 = s_62.sub<2>(4);
  ret_63[2] = s_61.sub<1>(((int)(s_66)));
  bitvec<2> s_67 = s_62.sub<2>(6);
  ret_63[3] = s_61.sub<1>(((int)(s_67)));
  p1_3[4] = ret_63;
  s_1 = p1_3;
}
void transpose(fixedarr<int, 16>  mx_0, fixedarr<int, 16> & s_1) {
  int x_2 = 0; int y_3 = 0;
  fixedarr<int, 16> T_4 = fixedarr< int,16>().v(0,0).v(1,0).v(2,0).v(3,0).v(4,0).v(5,0).v(6,0).v(7,0).v(8,0).v(9,0).v(10,0).v(11,0).v(12,0).v(13,0).v(14,0).v(15,0);
  bitvec<1> s_5 = bitvec<1>(0U);
  x_2 = 0;
  for (int s_6 = 0; (s_6) < (8); s_6 = s_6 + 1) {
    if ((s_5) == (bitvec<1>(0U))) {
      if ((x_2) < (4)) {
        bitvec<1> s_7 = bitvec<1>(0U);
        y_3 = 0;
        for (int s_8 = 0; (s_8) < (8); s_8 = s_8 + 1) {
          if ((s_7) == (bitvec<1>(0U))) {
            if ((y_3) < (4)) {
              T_4[(4 * x_2) + y_3] = mx_0.sub<1>((4 * y_3) + x_2);
              y_3;
              y_3 = y_3 + 1;
            } else {
              s_7 = bitvec<1>(1U);
            }
          }
        }
        x_2;
        x_2 = x_2 + 1;
      } else {
        s_5 = bitvec<1>(1U);
      }
    }
  }
  s_1 = T_4;
}

int main() {
    fixedarr<int, 16>  mx_0, sw, sse;
    for (int a = 0; a < 16; a++) {
        mx_0[a] = a;
    }
    std::cout << "initial matrix        " << mx_0 << std::endl;
    transpose(mx_0, sw);
    std::cout << "software transpose    " << sw << std::endl;
    sse_transpose(mx_0, sse);
    std::cout << "sse transpose         " << sse << std::endl;
}
