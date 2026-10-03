package dev.livingrealms.sim.data;

import java.util.*;

/** Small dependency-free RFC-8259 JSON reader used by the headless simulation/data tooling. */
public final class MiniJson {
    private MiniJson() {}

    public static Object parse(String json) {
        if (json == null) throw new IllegalArgumentException("json");
        Parser p = new Parser(json);
        Object value = p.value();
        p.ws();
        if (!p.eof()) throw p.error("Trailing content");
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object v, StringBuilder out, int depth) {
        if (v == null) { out.append("null"); return; }
        if (v instanceof String s) { quote(s, out); return; }
        if (v instanceof Boolean || v instanceof Number) { out.append(v); return; }
        if (v instanceof Map<?,?> map) {
            out.append('{');
            boolean first = true;
            for (var e : map.entrySet()) {
                if (!(e.getKey() instanceof String k)) throw new IllegalArgumentException("JSON object keys must be strings");
                if (!first) out.append(',');
                first=false; quote(k,out); out.append(':'); write(e.getValue(),out,depth+1);
            }
            out.append('}'); return;
        }
        if (v instanceof Iterable<?> list) {
            out.append('['); boolean first=true;
            for (Object item:list) { if(!first)out.append(','); first=false; write(item,out,depth+1); }
            out.append(']'); return;
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + v.getClass());
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s; private int i;
        Parser(String s){this.s=s;}
        boolean eof(){return i>=s.length();}
        IllegalArgumentException error(String msg){return new IllegalArgumentException(msg+" at offset "+i);}
        void ws(){while(!eof()){char c=s.charAt(i);if(c==' '||c=='\n'||c=='\r'||c=='\t')i++;else break;}}
        Object value(){
            ws(); if(eof())throw error("Expected value"); char c=s.charAt(i);
            return switch(c){
                case '{' -> object(); case '[' -> array(); case '"' -> string();
                case 't' -> literal("true",Boolean.TRUE); case 'f' -> literal("false",Boolean.FALSE); case 'n' -> literal("null",null);
                default -> { if(c=='-'||(c>='0'&&c<='9')) yield number(); throw error("Unexpected character '"+c+"'"); }
            };
        }
        Object literal(String token,Object value){if(!s.startsWith(token,i))throw error("Expected "+token);i+=token.length();return value;}
        Map<String,Object> object(){
            i++; ws(); Map<String,Object> out=new LinkedHashMap<>(); if(peek('}')){i++;return out;}
            while(true){ws();if(eof()||s.charAt(i)!='"')throw error("Expected object key");String k=string();ws();expect(':');Object v=value();if(out.put(k,v)!=null)throw error("Duplicate key '"+k+"'");ws();if(peek('}')){i++;return out;}expect(',');}
        }
        List<Object> array(){
            i++; ws(); List<Object> out=new ArrayList<>(); if(peek(']')){i++;return out;}
            while(true){out.add(value());ws();if(peek(']')){i++;return out;}expect(',');}
        }
        String string(){
            expect('"');StringBuilder out=new StringBuilder();
            while(!eof()){
                char c=s.charAt(i++);if(c=='"')return out.toString();if(c=='\\'){
                    if(eof())throw error("Unterminated escape");char e=s.charAt(i++);
                    switch(e){case '"'->out.append('"');case '\\'->out.append('\\');case '/'->out.append('/');case 'b'->out.append('\b');case 'f'->out.append('\f');case 'n'->out.append('\n');case 'r'->out.append('\r');case 't'->out.append('\t');case 'u'->out.append(unicode());default->throw error("Bad escape");}
                } else {if(c<0x20)throw error("Control char in string");out.append(c);}
            }throw error("Unterminated string");
        }
        char unicode(){if(i+4>s.length())throw error("Short unicode escape");int v=0;for(int n=0;n<4;n++){char c=s.charAt(i++);int d=Character.digit(c,16);if(d<0)throw error("Bad unicode escape");v=(v<<4)|d;}return(char)v;}
        Number number(){
            int start=i;if(peek('-'))i++;if(eof())throw error("Bad number");
            if(peek('0'))i++;else{if(!digit())throw error("Bad number");while(digit())i++;}
            boolean decimal=false;if(peek('.')){decimal=true;i++;if(!digit())throw error("Bad fraction");while(digit())i++;}
            if(peek('e')||peek('E')){decimal=true;i++;if(peek('+')||peek('-'))i++;if(!digit())throw error("Bad exponent");while(digit())i++;}
            String raw=s.substring(start,i);try{return decimal?Double.parseDouble(raw):Long.parseLong(raw);}catch(NumberFormatException ex){throw error("Bad number");}
        }
        boolean digit(){return !eof()&&s.charAt(i)>='0'&&s.charAt(i)<='9';}
        boolean peek(char c){return !eof()&&s.charAt(i)==c;}
        void expect(char c){ws();if(eof()||s.charAt(i)!=c)throw error("Expected '"+c+"'");i++;}
    }
}
