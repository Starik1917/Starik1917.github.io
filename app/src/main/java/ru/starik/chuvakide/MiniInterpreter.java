package ru.starik.chuvakide;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiniInterpreter {
    private final Map<String, Object> vars = new LinkedHashMap<>();
    private final StringBuilder output = new StringBuilder();
    private static final int LOOP_LIMIT = 10000;

    public String run(String rawSource) throws LangException {
        vars.clear();
        output.setLength(0);
        String source = rawSource;
        if (ChuvakCodec.looksEncoded(source)) {
            try { source = ChuvakCodec.decode(source); }
            catch (IllegalArgumentException e) { throw new LangException(1, e.getMessage()); }
        }
        List<SourceLine> lines = preprocess(source);
        ParseResult parsed = parseBlock(lines, 0, 0);
        if (parsed.nextIndex < lines.size()) {
            SourceLine line = lines.get(parsed.nextIndex);
            throw new LangException(line.number, "Неожиданный отступ");
        }
        for (Stmt stmt : parsed.statements) stmt.exec();
        return output.length() == 0 ? "✓ Программа завершилась без вывода" : output.toString();
    }

    private List<SourceLine> preprocess(String source) {
        List<SourceLine> result = new ArrayList<>();
        String[] raw = source.replace("\t", "    ").replace("\r", "").split("\n", -1);
        for (int i = 0; i < raw.length; i++) {
            String s = raw[i];
            int indent = 0;
            while (indent < s.length() && s.charAt(indent) == ' ') indent++;
            String text = s.substring(indent).trim();
            result.add(new SourceLine(indent, text, i + 1));
        }
        return result;
    }

    private ParseResult parseBlock(List<SourceLine> lines, int start, int indent) throws LangException {
        List<Stmt> out = new ArrayList<>();
        int i = start;
        while (i < lines.size()) {
            SourceLine line = lines.get(i);
            if (line.text.isEmpty() || line.text.startsWith("#")) { i++; continue; }
            if (line.indent < indent) break;
            if (line.indent > indent) throw new LangException(line.number, "Лишний отступ");

            if (line.text.startsWith("если ") && line.text.endsWith(":")) {
                String condText = line.text.substring(5, line.text.length() - 1).trim();
                Expr cond = parseExpr(condText, line.number);
                int childIndent = findChildIndent(lines, i + 1, indent, line.number);
                ParseResult yes = parseBlock(lines, i + 1, childIndent);
                i = yes.nextIndex;
                List<Stmt> no = Collections.emptyList();
                int probe = skipIgnorable(lines, i);
                if (probe < lines.size()) {
                    SourceLine maybeElse = lines.get(probe);
                    if (maybeElse.indent == indent && maybeElse.text.equals("иначе:")) {
                        int elseIndent = findChildIndent(lines, probe + 1, indent, maybeElse.number);
                        ParseResult nope = parseBlock(lines, probe + 1, elseIndent);
                        no = nope.statements;
                        i = nope.nextIndex;
                    }
                }
                out.add(new IfStmt(line.number, cond, yes.statements, no));
                continue;
            }

            if (line.text.startsWith("пока ") && line.text.endsWith(":")) {
                String condText = line.text.substring(5, line.text.length() - 1).trim();
                Expr cond = parseExpr(condText, line.number);
                int childIndent = findChildIndent(lines, i + 1, indent, line.number);
                ParseResult body = parseBlock(lines, i + 1, childIndent);
                out.add(new WhileStmt(line.number, cond, body.statements));
                i = body.nextIndex;
                continue;
            }

            if (line.text.equals("иначе:")) break;
            out.add(parseSimple(line));
            i++;
        }
        return new ParseResult(out, i);
    }

    private int skipIgnorable(List<SourceLine> lines, int i) {
        while (i < lines.size()) {
            String t = lines.get(i).text;
            if (!t.isEmpty() && !t.startsWith("#")) break;
            i++;
        }
        return i;
    }

    private int findChildIndent(List<SourceLine> lines, int start, int parentIndent, int lineNo) throws LangException {
        int i = skipIgnorable(lines, start);
        if (i >= lines.size() || lines.get(i).indent <= parentIndent) {
            throw new LangException(lineNo, "После ':' нужен блок с отступом");
        }
        return lines.get(i).indent;
    }

    private Stmt parseSimple(SourceLine line) throws LangException {
        String t = line.text;
        if (t.startsWith("печать(") && t.endsWith(")")) {
            String inner = t.substring("печать(".length(), t.length() - 1);
            return new PrintStmt(line.number, parseExpr(inner, line.number));
        }
        Matcher m = Pattern.compile("^([\\p{L}_][\\p{L}\\p{N}_]*)\\s*=\\s*(.+)$").matcher(t);
        if (m.matches() && !t.contains("==")) {
            return new AssignStmt(line.number, m.group(1), parseExpr(m.group(2), line.number));
        }
        throw new LangException(line.number, "Не понимаю команду: " + t);
    }

    private Expr parseExpr(String text, int line) throws LangException {
        ExprParser p = new ExprParser(text, line);
        return p.parse();
    }

    private interface Stmt { void exec() throws LangException; }
    private interface Expr { Object eval() throws LangException; }

    private final class PrintStmt implements Stmt {
        final int line; final Expr expr;
        PrintStmt(int line, Expr expr) { this.line=line; this.expr=expr; }
        public void exec() throws LangException { output.append(format(expr.eval())).append('\n'); }
    }

    private final class AssignStmt implements Stmt {
        final int line; final String name; final Expr expr;
        AssignStmt(int line, String name, Expr expr) { this.line=line; this.name=name; this.expr=expr; }
        public void exec() throws LangException { vars.put(name, expr.eval()); }
    }

    private final class IfStmt implements Stmt {
        final int line; final Expr cond; final List<Stmt> yes, no;
        IfStmt(int line, Expr cond, List<Stmt> yes, List<Stmt> no) { this.line=line; this.cond=cond; this.yes=yes; this.no=no; }
        public void exec() throws LangException {
            List<Stmt> chosen = truthy(cond.eval()) ? yes : no;
            for (Stmt s : chosen) s.exec();
        }
    }

    private final class WhileStmt implements Stmt {
        final int line; final Expr cond; final List<Stmt> body;
        WhileStmt(int line, Expr cond, List<Stmt> body) { this.line=line; this.cond=cond; this.body=body; }
        public void exec() throws LangException {
            int n=0;
            while (truthy(cond.eval())) {
                if (++n > LOOP_LIMIT) throw new LangException(line, "Цикл остановлен после " + LOOP_LIMIT + " итераций");
                for (Stmt s : body) s.exec();
            }
        }
    }

    private final class ExprParser {
        final List<Token> tokens; int pos=0; final int line;
        ExprParser(String src, int line) throws LangException { this.tokens = lex(src, line); this.line=line; }
        Expr parse() throws LangException {
            Expr e = parseOr();
            if (!peek("EOF")) throw error("Лишний фрагмент выражения: " + current().text);
            return e;
        }
        Expr parseOr() throws LangException {
            Expr e=parseAnd();
            while (matchWord("или")) { Expr l=e,r=parseAnd(); e=()-> truthy(l.eval()) || truthy(r.eval()); }
            return e;
        }
        Expr parseAnd() throws LangException {
            Expr e=parseEquality();
            while (matchWord("и")) { Expr l=e,r=parseEquality(); e=()-> truthy(l.eval()) && truthy(r.eval()); }
            return e;
        }
        Expr parseEquality() throws LangException {
            Expr e=parseComparison();
            while (matchOp("==") || matchOp("!=")) {
                String op=previous().text; Expr l=e,r=parseComparison();
                e=()-> op.equals("==") ? equal(l.eval(),r.eval()) : !equal(l.eval(),r.eval());
            }
            return e;
        }
        Expr parseComparison() throws LangException {
            Expr e=parseTerm();
            while (matchOp("<") || matchOp("<=") || matchOp(">") || matchOp(">=")) {
                String op=previous().text; Expr l=e,r=parseTerm();
                e=()-> compare(l.eval(),r.eval(),op,line);
            }
            return e;
        }
        Expr parseTerm() throws LangException {
            Expr e=parseFactor();
            while (matchOp("+") || matchOp("-")) {
                String op=previous().text; Expr l=e,r=parseFactor();
                e=()-> binary(l.eval(),r.eval(),op,line);
            }
            return e;
        }
        Expr parseFactor() throws LangException {
            Expr e=parseUnary();
            while (matchOp("*") || matchOp("/") || matchOp("%")) {
                String op=previous().text; Expr l=e,r=parseUnary();
                e=()-> binary(l.eval(),r.eval(),op,line);
            }
            return e;
        }
        Expr parseUnary() throws LangException {
            if (matchOp("-")) { Expr r=parseUnary(); return ()-> -num(r.eval(),line); }
            if (matchWord("не")) { Expr r=parseUnary(); return ()-> !truthy(r.eval()); }
            return parsePrimary();
        }
        Expr parsePrimary() throws LangException {
            Token t=current();
            if (match("NUMBER")) { double d=Double.parseDouble(t.text); return ()-> d; }
            if (match("STRING")) { String s=t.text; return ()-> s; }
            if (matchWord("истина")) return ()-> true;
            if (matchWord("ложь")) return ()-> false;
            if (match("IDENT")) {
                String name=t.text;
                if (match("LPAREN")) {
                    Expr arg=parseOr(); consume("RPAREN", "Ожидалась ')' после аргумента");
                    if (name.equals("длина")) return ()-> length(arg.eval(), line);
                    throw error("Неизвестная функция: " + name);
                }
                return ()->{ if (!vars.containsKey(name)) throw new LangException(line,"Переменная не определена: "+name); return vars.get(name); };
            }
            if (match("LPAREN")) { Expr e=parseOr(); consume("RPAREN","Ожидалась ')' "); return e; }
            if (match("LBRACK")) {
                List<Expr> items=new ArrayList<>();
                if (!peek("RBRACK")) {
                    do { items.add(parseOr()); } while (match("COMMA"));
                }
                consume("RBRACK","Ожидалась ']'");
                return ()->{ List<Object> v=new ArrayList<>(); for(Expr x:items)v.add(x.eval()); return v; };
            }
            throw error("Ожидалось значение, получено: " + t.text);
        }
        boolean match(String type) { if(peek(type)){pos++;return true;}return false; }
        boolean matchOp(String op) { if(current().type.equals("OP")&&current().text.equals(op)){pos++;return true;}return false; }
        boolean matchWord(String w) { if(current().type.equals("IDENT")&&current().text.equals(w)){pos++;return true;}return false; }
        void consume(String type,String msg) throws LangException { if(!match(type))throw error(msg); }
        boolean peek(String type){return current().type.equals(type);} Token current(){return tokens.get(pos);} Token previous(){return tokens.get(pos-1);} LangException error(String m){return new LangException(line,m);}
    }

    private List<Token> lex(String s, int line) throws LangException {
        List<Token> out=new ArrayList<>(); int i=0;
        while(i<s.length()) {
            char c=s.charAt(i);
            if(Character.isWhitespace(c)){i++;continue;}
            if(Character.isDigit(c) || (c=='.' && i+1<s.length() && Character.isDigit(s.charAt(i+1)))) {
                int st=i; boolean dot=false;
                while(i<s.length() && (Character.isDigit(s.charAt(i)) || (!dot && s.charAt(i)=='.'))) { if(s.charAt(i)=='.')dot=true; i++; }
                out.add(new Token("NUMBER",s.substring(st,i))); continue;
            }
            if(c=='\'' || c=='\"') {
                char q=c; i++; StringBuilder b=new StringBuilder(); boolean closed=false;
                while(i<s.length()) { char x=s.charAt(i++); if(x==q){closed=true;break;} if(x=='\\'&&i<s.length()){char n=s.charAt(i++); b.append(n=='n'?'\n':n=='t'?'\t':n);} else b.append(x); }
                if(!closed) throw new LangException(line,"Незакрытая строка"); out.add(new Token("STRING",b.toString())); continue;
            }
            if(Character.isLetter(c)||c=='_') { int st=i++; while(i<s.length()&&(Character.isLetterOrDigit(s.charAt(i))||s.charAt(i)=='_'))i++; out.add(new Token("IDENT",s.substring(st,i).toLowerCase(Locale.ROOT))); continue; }
            if(i+1<s.length()) { String two=s.substring(i,i+2); if(Arrays.asList("==","!=","<=",">=").contains(two)){out.add(new Token("OP",two));i+=2;continue;} }
            if("+-*/%<>".indexOf(c)>=0){out.add(new Token("OP",String.valueOf(c)));i++;continue;}
            if(c=='('){out.add(new Token("LPAREN","("));i++;continue;} if(c==')'){out.add(new Token("RPAREN",")"));i++;continue;}
            if(c=='['){out.add(new Token("LBRACK","["));i++;continue;} if(c==']'){out.add(new Token("RBRACK","]"));i++;continue;}
            if(c==','){out.add(new Token("COMMA",","));i++;continue;}
            throw new LangException(line,"Неизвестный символ в выражении: "+c);
        }
        out.add(new Token("EOF","конец")); return out;
    }

    private static Object binary(Object a,Object b,String op,int line) throws LangException {
        if(op.equals("+")) {
            if(a instanceof String || b instanceof String) return format(a)+format(b);
            if(a instanceof List && b instanceof List){List<Object> x=new ArrayList<>((List<?>)a);x.addAll((List<?>)b);return x;}
        }
        double x=num(a,line), y=num(b,line);
        switch(op){case "+":return x+y;case "-":return x-y;case "*":return x*y;case "/":if(y==0)throw new LangException(line,"Деление на ноль");return x/y;case "%":if(y==0)throw new LangException(line,"Деление на ноль");return x%y;default:throw new LangException(line,"Неизвестный оператор "+op);}
    }
    private static boolean compare(Object a,Object b,String op,int line) throws LangException { double x=num(a,line),y=num(b,line); switch(op){case "<":return x<y;case "<=":return x<=y;case ">":return x>y;case ">=":return x>=y;} return false; }
    private static boolean equal(Object a,Object b){ if(a instanceof Number&&b instanceof Number)return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue())==0; return Objects.equals(a,b); }
    private static double num(Object v,int line)throws LangException{if(v instanceof Number)return((Number)v).doubleValue();throw new LangException(line,"Нужно число, получено: "+format(v));}
    private static boolean truthy(Object v){if(v==null)return false;if(v instanceof Boolean)return(Boolean)v;if(v instanceof Number)return((Number)v).doubleValue()!=0;if(v instanceof String)return!((String)v).isEmpty();if(v instanceof Collection)return!((Collection<?>)v).isEmpty();return true;}
    private static int length(Object v,int line)throws LangException{if(v instanceof String)return((String)v).length();if(v instanceof Collection)return((Collection<?>)v).size();throw new LangException(line,"длина() работает со строками и списками");}
    private static String format(Object v){if(v==null)return"ничего";if(v instanceof Double){double d=(Double)v;if(d==Math.rint(d))return Long.toString((long)d);}if(v instanceof List){StringBuilder b=new StringBuilder("[");List<?> l=(List<?>)v;for(int i=0;i<l.size();i++){if(i>0)b.append(", ");b.append(format(l.get(i)));}return b.append(']').toString();}return String.valueOf(v);}

    private static final class Token { final String type,text; Token(String type,String text){this.type=type;this.text=text;} }
    private static final class SourceLine { final int indent,number; final String text; SourceLine(int indent,String text,int number){this.indent=indent;this.text=text;this.number=number;} }
    private static final class ParseResult { final List<Stmt> statements; final int nextIndex; ParseResult(List<Stmt>s,int n){statements=s;nextIndex=n;} }
    public static final class LangException extends Exception { public final int line; LangException(int line,String msg){super("Строка "+line+": "+msg);this.line=line;} }
}
