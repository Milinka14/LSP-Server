grammar Logo;

program
    : statement* EOF
    ;

statement
    : procedureDefinition
    | defineProcedure
    | variableAssignment
    | localDeclaration
    | controlStructure
    | command
    ;

procedureDefinition
    : TO name=ID parameterDecl* statement* END
    ;

defineProcedure
    : DEFINE QUOTED_ID listLiteral
    | DEFINE name=ID parameterDecl* statement* END
    ;

localDeclaration
    : LOCAL QUOTED_ID+
    ;

parameterDecl
    : COLON ID
    ;

variableAssignment
    : MAKE QUOTED_ID value=expression
    | NAME value=expression QUOTED_ID
    | LOCALMAKE QUOTED_ID value=expression
    ;

command
    : FORWARD expression
    | BACK expression
    | LEFT expression
    | RIGHT expression
    | SETX expression
    | SETY expression
    | SETXY expression expression
    | SETPOS argument
    | SET POS argument
    | SETHEADING expression
    | SETH expression
    | SH expression
    | ARC expression expression
    | ELLIPSE expression expression
    | PENDOWN
    | PENUP
    | SHOWTURTLE
    | ST
    | HIDETURTLE
    | HT
    | CLEAN
    | CLEARSCREEN
    | CS
    | FILL
    | FILLED expression block
    | LABEL expression
    | SETLABELHEIGHT expression
    | WRAP
    | WINDOW
    | FENCE
    | SETCOLOR argument
    | SETPENCOLOR argument
    | SETWIDTH expression
    | SETPENSIZE expression
    | CHANGESHAPE expression
    | CSH expression
    | SHOW expression
    | PRINT expression
    | POS
    | XCOR
    | YCOR
    | HEADING
    | TOWARDS argument
    | SHOWNP
    | SHOWNQ
    | LABELSIZE
    | PENDOWNP
    | PENDOWNQ
    | PENCOLOR
    | PC
    | PENSIZE
    | REPCOUNT
    | HOME
    | BYE
    | STOP
    | variable
    | procedureCall
    ;

procedureCall
    : name=ID argument*
    ;

callExpression
    : name=ID argument+
    ;

argument
    : expression
    | listLiteral
    ;

controlStructure
    : REPEAT expression LBRACK statement* RBRACK
    | FOREVER block
    | IF expression block
    | IFELSE expression block block
    | TEST expression
    | IFTRUE block
    | IFFALSE block
    | WAIT expression
    | FOR forControlList block
    | DOTIMES dotimesControlList block
    | DOWHILE block expression
    | WHILE expression block
    | WHILE block block
    | DOUNTIL block expression
    | DOUNTIL block block
    | UNTIL expression block
    | UNTIL block block
    ;

block
    : LBRACK listElement* RBRACK
    ;

forControlList
    : LBRACK ID expression expression expression? RBRACK
    ;

dotimesControlList
    : LBRACK ID expression RBRACK
    ;

expression
    : additiveExpression ((LT | GT | EQ) additiveExpression)*
    ;

additiveExpression
    : term ((PLUS | MINUS) term)*
    ;

term
    : factor ((STAR | SLASH) factor)*
    ;

factor
    : NUMBER
    | MINUS factor
    | QUOTED_ID
    | DEF QUOTED_ID
    | THING argument
    | LIST argument+
    | FIRST argument
    | BUTFIRST argument
    | LAST argument
    | BUTLAST argument
    | ITEM expression argument
    | PICK argument
    | SUM expression expression
    | MINUSFN expression expression
    | RANDOM expression
    | MODULO expression expression
    | REMAINDER expression expression
    | POWER expression expression
    | READWORD argument?
    | READLIST argument?
    | WORD argument
    | WORDQ argument
    | LISTP argument
    | LISTQ argument
    | ARRAYP argument
    | ARRAYQ argument
    | NUMBERP argument
    | NUMBERQ argument
    | EMPTYP argument
    | EMPTYQ argument
    | EQUALP expression expression
    | EQUALQ expression expression
    | NOTEQUALP expression expression
    | NOTEQUALQ expression expression
    | BEFOREP expression expression
    | BEFOREQ expression expression
    | SUBSTRINGP expression expression
    | SUBSTRINGQ expression expression
    | ARRAY expression
    | FPUT expression argument
    | LPUT expression argument
    | POS
    | XCOR
    | YCOR
    | HEADING
    | LABELSIZE
    | SHOWNP
    | SHOWNQ
    | PENDOWNP
    | PENDOWNQ
    | PENCOLOR
    | PC
    | PENSIZE
    | REPCOUNT
    | TOWARDS argument
    | variable
    | callExpression
    | ID
    | listLiteral
    | LPAREN expression RPAREN
    ;

variable
    : COLON ID
    ;

listLiteral
    : LBRACK listElement* RBRACK
    ;

listElement
    : statement
    | expression
    ;

TO      : [tT] [oO] ;
END     : [eE] [nN] [dD] ;
MAKE    : [mM] [aA] [kK] [eE] ;
FORWARD : ([fF] [oO] [rR] [wW] [aA] [rR] [dD] | [fF] [dD]) ;
BACK    : ([bB] [aA] [cC] [kK] | [bB] [kK]) ;
LEFT    : ([lL] [eE] [fF] [tT] | [lL] [tT]) ;
RIGHT   : ([rR] [iI] [gG] [hH] [tT] | [rR] [tT]) ;
PENDOWN : ([pP] [eE] [nN] [dD] [oO] [wW] [nN] | [pP] [dD]) ;
PENUP   : ([pP] [eE] [nN] [uU] [pP] | [pP] [uU]) ;
CLEAN   : [cC] [lL] [eE] [aA] [nN] ;
HOME    : [hH] [oO] [mM] [eE] ;
SETX    : [sS] [eE] [tT] [xX] ;
SETY    : [sS] [eE] [tT] [yY] ;
SETXY   : [sS] [eE] [tT] [xX] [yY] ;
SETPOS  : [sS] [eE] [tT] [pP] [oO] [sS] ;
SETHEADING : [sS] [eE] [tT] [hH] [eE] [aA] [dD] [iI] [nN] [gG] ;
SETH    : [sS] [eE] [tT] [hH] ;
SH      : [sS] [hH] ;
ARC     : [aA] [rR] [cC] ;
ELLIPSE : [eE] [lL] [lL] [iI] [pP] [sS] [eE] ;
REPEAT  : [rR] [eE] [pP] [eE] [aA] [tT] ;
IFELSE  : [iI] [fF] [eE] [lL] [sS] [eE] ;
IF      : [iI] [fF] ;
TEST    : [tT] [eE] [sS] [tT] ;
IFTRUE  : [iI] [fF] [tT] [rR] [uU] [eE] ;
IFFALSE : [iI] [fF] [fF] [aA] [lL] [sS] [eE] ;
WAIT    : [wW] [aA] [iI] [tT] ;
FOR     : [fF] [oO] [rR] ;
DOTIMES : [dD] [oO] [tT] [iI] [mM] [eE] [sS] ;
DOWHILE : [dD] [oO] '.' [wW] [hH] [iI] [lL] [eE] ;
WHILE   : [wW] [hH] [iI] [lL] [eE] ;
DOUNTIL : [dD] [oO] '.' [uU] [nN] [tT] [iI] [lL] ;
UNTIL   : [uU] [nN] [tT] [iI] [lL] ;
NAME    : [nN] [aA] [mM] [eE] ;
LOCALMAKE : [lL] [oO] [cC] [aA] [lL] [mM] [aA] [kK] [eE] ;
SHOWTURTLE : [sS] [hH] [oO] [wW] [tT] [uU] [rR] [tT] [lL] [eE] ;
ST      : [sS] [tT] ;
HIDETURTLE : [hH] [iI] [dD] [eE] [tT] [uU] [rR] [tT] [lL] [eE] ;
HT      : [hH] [tT] ;
CLEARSCREEN : [cC] [lL] [eE] [aA] [rR] [sS] [cC] [rR] [eE] [eE] [nN] ;
CS      : [cC] [sS] ;
FILL    : [fF] [iI] [lL] [lL] ;
FILLED  : [fF] [iI] [lL] [lL] [eE] [dD] ;
LABEL   : [lL] [aA] [bB] [eE] [lL] ;
SETLABELHEIGHT : [sS] [eE] [tT] [lL] [aA] [bB] [eE] [lL] [hH] [eE] [iI] [gG] [hH] [tT] ;
WRAP    : [wW] [rR] [aA] [pP] ;
WINDOW  : [wW] [iI] [nN] [dD] [oO] [wW] ;
FENCE   : [fF] [eE] [nN] [cC] [eE] ;
SETCOLOR : [sS] [eE] [tT] [cC] [oO] [lL] [oO] [rR] ;
SETPENCOLOR : [sS] [eE] [tT] [pP] [eE] [nN] [cC] [oO] [lL] [oO] [rR] ;
SETWIDTH : [sS] [eE] [tT] [wW] [iI] [dD] [tT] [hH] ;
SETPENSIZE : [sS] [eE] [tT] [pP] [eE] [nN] [sS] [iI] [zZ] [eE] ;
CHANGESHAPE : [cC] [hH] [aA] [nN] [gG] [eE] [sS] [hH] [aA] [pP] [eE] ;
CSH     : [cC] [sS] [hH] ;
SHOW    : [sS] [hH] [oO] [wW] ;
PRINT   : [pP] [rR] [iI] [nN] [tT] ;
POS     : [pP] [oO] [sS] ;
XCOR    : [xX] [cC] [oO] [rR] ;
YCOR    : [yY] [cC] [oO] [rR] ;
HEADING : [hH] [eE] [aA] [dD] [iI] [nN] [gG] ;
TOWARDS : [tT] [oO] [wW] [aA] [rR] [dD] [sS] ;
SHOWNP  : [sS] [hH] [oO] [wW] [nN] [pP] ;
SHOWNQ  : [sS] [hH] [oO] [wW] [nN] '?' ;
LABELSIZE : [lL] [aA] [bB] [eE] [lL] [sS] [iI] [zZ] [eE] ;
PENDOWNP : [pP] [eE] [nN] [dD] [oO] [wW] [nN] [pP] ;
PENDOWNQ : [pP] [eE] [nN] [dD] [oO] [wW] [nN] '?' ;
PENCOLOR : [pP] [eE] [nN] [cC] [oO] [lL] [oO] [rR] ;
PC      : [pP] [cC] ;
PENSIZE : [pP] [eE] [nN] [sS] [iI] [zZ] [eE] ;
REPCOUNT : [rR] [eE] [pP] [cC] [oO] [uU] [nN] [tT] ;
THING   : [tT] [hH] [iI] [nN] [gG] ;
LIST    : [lL] [iI] [sS] [tT] ;
FIRST   : [fF] [iI] [rR] [sS] [tT] ;
BUTFIRST : [bB] [uU] [tT] [fF] [iI] [rR] [sS] [tT] ;
LAST    : [lL] [aA] [sS] [tT] ;
BUTLAST : [bB] [uU] [tT] [lL] [aA] [sS] [tT] ;
ITEM    : [iI] [tT] [eE] [mM] ;
PICK    : [pP] [iI] [cC] [kK] ;
SUM     : [sS] [uU] [mM] ;
MINUSFN : [mM] [iI] [nN] [uU] [sS] ;
RANDOM  : [rR] [aA] [nN] [dD] [oO] [mM] ;
MODULO  : [mM] [oO] [dD] [uU] [lL] [oO] ;
REMAINDER : [rR] [eE] [mM] [aA] [iI] [nN] [dD] [eE] [rR] ;
POWER   : [pP] [oO] [wW] [eE] [rR] ;
READWORD : [rR] [eE] [aA] [dD] [wW] [oO] [rR] [dD] ;
READLIST : [rR] [eE] [aA] [dD] [lL] [iI] [sS] [tT] ;
WORD    : [wW] [oO] [rR] [dD] ;
WORDQ   : [wW] [oO] [rR] [dD] '?' ;
LISTP   : [lL] [iI] [sS] [tT] [pP] ;
LISTQ   : [lL] [iI] [sS] [tT] '?' ;
ARRAYP  : [aA] [rR] [rR] [aA] [yY] [pP] ;
ARRAYQ  : [aA] [rR] [rR] [aA] [yY] '?' ;
NUMBERP : [nN] [uU] [mM] [bB] [eE] [rR] [pP] ;
NUMBERQ : [nN] [uU] [mM] [bB] [eE] [rR] '?' ;
EMPTYP  : [eE] [mM] [pP] [tT] [yY] [pP] ;
EMPTYQ  : [eE] [mM] [pP] [tT] [yY] '?' ;
EQUALP  : [eE] [qQ] [uU] [aA] [lL] [pP] ;
EQUALQ  : [eE] [qQ] [uU] [aA] [lL] '?' ;
NOTEQUALP : [nN] [oO] [tT] [eE] [qQ] [uU] [aA] [lL] [pP] ;
NOTEQUALQ : [nN] [oO] [tT] [eE] [qQ] [uU] [aA] [lL] '?' ;
BEFOREP : [bB] [eE] [fF] [oO] [rR] [eE] [pP] ;
BEFOREQ : [bB] [eE] [fF] [oO] [rR] [eE] '?' ;
SUBSTRINGP : [sS] [uU] [bB] [sS] [tT] [rR] [iI] [nN] [gG] [pP] ;
SUBSTRINGQ : [sS] [uU] [bB] [sS] [tT] [rR] [iI] [nN] [gG] '?' ;
ARRAY   : [aA] [rR] [rR] [aA] [yY] ;
FPUT    : [fF] [pP] [uU] [tT] ;
LPUT    : [lL] [pP] [uU] [tT] ;
BYE     : [bB] [yY] [eE] ;
STOP    : [sS] [tT] [oO] [pP] ;
FOREVER : [fF] [oO] [rR] [eE] [vV] [eE] [rR] ;
DEFINE  : [dD] [eE] [fF] [iI] [nN] [eE] ;
DEF     : [dD] [eE] [fF] ;
LOCAL   : [lL] [oO] [cC] [aA] [lL] ;
SET     : [sS] [eE] [tT] ;

LBRACK : '[' ;
RBRACK : ']' ;
LPAREN : '(' ;
RPAREN : ')' ;
COLON  : ':' ;

QUOTED_ID : '"' [a-zA-Z] [a-zA-Z0-9_?.]* ;
ID        : [a-zA-Z] [a-zA-Z0-9_?.]* ;
NUMBER    : [0-9]+ ('.' [0-9]+)? ;

PLUS  : '+' ;
MINUS : '-' ;
STAR  : '*' ;
SLASH : '/' ;
LT    : '<' ;
GT    : '>' ;
EQ    : '=' ;
COMMA : ',' ;

WS      : [ \t\r\n]+ -> skip ;
COMMENT : ';' ~[\r\n]* -> channel(HIDDEN) ;

