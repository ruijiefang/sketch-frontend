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

import sketch.compiler.Directive;
import sketch.compiler.ast.core.FEContext;
import sketch.compiler.ast.core.FieldDecl;
import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.Program;
import sketch.compiler.ast.core.SplitterJoiner;
import sketch.compiler.ast.core.StreamSpec;
import sketch.compiler.ast.core.StreamType;

import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.*;
import sketch.compiler.ast.cuda.exprs.*;
import sketch.compiler.ast.cuda.stmts.*;
import sketch.compiler.ast.cuda.typs.*;

import sketch.compiler.ast.promela.stmts.StmtFork;
import sketch.compiler.main.cmdline.SketchOptions;
import sketch.compiler.passes.streamit_old.SJDuplicate;
import sketch.compiler.passes.streamit_old.SJRoundRobin;
import sketch.compiler.passes.streamit_old.SJWeightedRR;
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
	int TK_complex = 20;
	int TK_struct = 21;
	int TK_ref = 22;
	int TK_if = 23;
	int TK_else = 24;
	int TK_while = 25;
	int TK_for = 26;
	int TK_switch = 27;
	int TK_case = 28;
	int TK_default = 29;
	int TK_break = 30;
	int TK_do = 31;
	int TK_continue = 32;
	int TK_return = 33;
	int TK_true = 34;
	int TK_false = 35;
	int TK_parfor = 36;
	int TK_until = 37;
	int TK_by = 38;
	int TK_implements = 39;
	int TK_assert = 40;
	int TK_h_assert = 41;
	int TK_generator = 42;
	int TK_harness = 43;
	int TK_library = 44;
	int TK_printfcn = 45;
	int TK_device = 46;
	int TK_global = 47;
	int TK_serial = 48;
	int TK_stencil = 49;
	int TK_include = 50;
	int TK_pragma = 51;
	int ARROW = 52;
	int LARROW = 53;
	int WS = 54;
	int LINERESET = 55;
	int SL_COMMENT = 56;
	int ML_COMMENT = 57;
	int LPAREN = 58;
	int RPAREN = 59;
	int LCURLY = 60;
	int RCURLY = 61;
	int LSQUARE = 62;
	int RSQUARE = 63;
	int PLUS = 64;
	int PLUS_EQUALS = 65;
	int INCREMENT = 66;
	int MINUS = 67;
	int MINUS_EQUALS = 68;
	int DECREMENT = 69;
	int STAR = 70;
	int STAR_EQUALS = 71;
	int DIV = 72;
	int DIV_EQUALS = 73;
	int MOD = 74;
	int LOGIC_AND = 75;
	int LOGIC_OR = 76;
	int BITWISE_AND = 77;
	int BITWISE_OR = 78;
	int BITWISE_XOR = 79;
	int ASSIGN = 80;
	int DEF_ASSIGN = 81;
	int EQUAL = 82;
	int NOT_EQUAL = 83;
	int LESS_THAN = 84;
	int LESS_EQUAL = 85;
	int MORE_THAN = 86;
	int MORE_EQUAL = 87;
	int QUESTION = 88;
	int COLON = 89;
	int SEMI = 90;
	int COMMA = 91;
	int DOT = 92;
	int BANG = 93;
	int LSHIFT = 94;
	int RSHIFT = 95;
	int NDVAL = 96;
	int NDVAL2 = 97;
	int SELECT = 98;
	int REGEN = 99;
	int CHAR_LITERAL = 100;
	int STRING_LITERAL = 101;
	int ESC = 102;
	int DIGIT = 103;
	int HQUAN = 104;
	int NUMBER = 105;
	int ID = 106;
	int TK_roundrobin = 107;
	int TK_duplicate = 108;
	int TK_pi = 109;
}
