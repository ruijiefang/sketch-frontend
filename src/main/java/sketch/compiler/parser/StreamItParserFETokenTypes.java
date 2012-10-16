// $ANTLR 2.7.7 (2006-11-01): "StreamItParserFE.g" -> "StreamItParserFE.java"$

package sketch.compiler.parser;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;

import sketch.compiler.Directive;
import sketch.compiler.ast.core.FEContext;
import sketch.compiler.ast.core.FieldDecl;
import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.Program;
import sketch.compiler.ast.core.Annotation;
import sketch.util.datastructures.HashmapList;

import sketch.compiler.ast.core.Package;


import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.exprs.regens.*;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.*;
import sketch.compiler.ast.cuda.exprs.*;
import sketch.compiler.ast.cuda.stmts.*;
import sketch.compiler.ast.cuda.typs.*;

import sketch.compiler.ast.promela.stmts.StmtFork;
import sketch.compiler.main.cmdline.SketchOptions;

import sketch.compiler.ast.spmd.stmts.StmtSpmdfork;

import static sketch.util.DebugOut.assertFalse;

public interface StreamItParserFETokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int TK_atomic = 4;
	int TK_fork = 5;
	int TK_insert = 6;
	int TK_into = 7;
	int TK_loop = 8;
	int TK_repeat = 9;
	int TK_minrepeat = 10;
	int TK_new = 11;
	int TK_null = 12;
	int TK_reorder = 13;
	int TK_boolean = 14;
	int TK_float = 15;
	int TK_bit = 16;
	int TK_int = 17;
	int TK_void = 18;
	int TK_double = 19;
	int TK_fun = 20;
	int TK_char = 21;
	int TK_struct = 22;
	int TK_ref = 23;
	int TK_if = 24;
	int TK_else = 25;
	int TK_while = 26;
	int TK_for = 27;
	int TK_switch = 28;
	int TK_case = 29;
	int TK_default = 30;
	int TK_break = 31;
	int TK_do = 32;
	int TK_continue = 33;
	int TK_return = 34;
	int TK_true = 35;
	int TK_false = 36;
	int TK_parfor = 37;
	int TK_until = 38;
	int TK_by = 39;
	int TK_implements = 40;
	int TK_assert = 41;
	int TK_assert_max = 42;
	int TK_h_assert = 43;
	int TK_generator = 44;
	int TK_harness = 45;
	int TK_library = 46;
	int TK_printfcn = 47;
	int TK_device = 48;
	int TK_global = 49;
	int TK_serial = 50;
	int TK_spmdfork = 51;
	int TK_stencil = 52;
	int TK_include = 53;
	int TK_pragma = 54;
	int TK_package = 55;
	int ARROW = 56;
	int LARROW = 57;
	int WS = 58;
	int LINERESET = 59;
	int SL_COMMENT = 60;
	int ML_COMMENT = 61;
	int LPAREN = 62;
	int RPAREN = 63;
	int LCURLY = 64;
	int RCURLY = 65;
	int LSQUARE = 66;
	int RSQUARE = 67;
	int PLUS = 68;
	int PLUS_EQUALS = 69;
	int INCREMENT = 70;
	int MINUS = 71;
	int MINUS_EQUALS = 72;
	int DECREMENT = 73;
	int STAR = 74;
	int STAR_EQUALS = 75;
	int DIV = 76;
	int DIV_EQUALS = 77;
	int MOD = 78;
	int LOGIC_AND = 79;
	int LOGIC_OR = 80;
	int BITWISE_AND = 81;
	int BITWISE_OR = 82;
	int BITWISE_XOR = 83;
	int ASSIGN = 84;
	int DEF_ASSIGN = 85;
	int EQUAL = 86;
	int NOT_EQUAL = 87;
	int LESS_THAN = 88;
	int LESS_EQUAL = 89;
	int MORE_THAN = 90;
	int MORE_EQUAL = 91;
	int QUESTION = 92;
	int COLON = 93;
	int SEMI = 94;
	int COMMA = 95;
	int DOT = 96;
	int BANG = 97;
	int LSHIFT = 98;
	int RSHIFT = 99;
	int NDVAL = 100;
	int NDVAL2 = 101;
	int SELECT = 102;
	int NDANGELIC = 103;
	int AT = 104;
	int BACKSLASH = 105;
	int LESS_COLON = 106;
	int REGEN = 107;
	int CHAR_LITERAL = 108;
	int STRING_LITERAL = 109;
	int ESC = 110;
	int DIGIT = 111;
	int HQUAN = 112;
	int NUMBER = 113;
	int ID = 114;
}
