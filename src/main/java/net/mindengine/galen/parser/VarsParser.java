/*******************************************************************************
* Copyright 2014 Ivan Shubin http://mindengine.net
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*   http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/
package net.mindengine.galen.parser;

import java.util.Map;
import java.util.Properties;

import net.mindengine.galen.specs.reader.StringCharReader;
import net.mindengine.galen.suite.reader.Context;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class VarsParser {

    private static final int PARSING_TEXT = 0;
    private static final int PARSING_PARAM = 1;
    private Context context;

    private int state = PARSING_TEXT;
    private VarsParserJsFunctions jsFunctions;
    private Properties properties;
   

    public VarsParser(Context context, Properties properties, VarsParserJsFunctions jsFunctions) {
        this.context = context;
        this.properties = properties;
        this.setJsFunctions(jsFunctions);
    }

    public String parse(String templateText) {
        StringCharReader reader = new StringCharReader(templateText);
        
        StringBuffer buffer = new StringBuffer();
        
        StringBuffer currentExpression = new StringBuffer();
        
        while(reader.hasMore()) {
            char symbol = reader.next();
            if (state ==  PARSING_TEXT) {
                if (symbol == '$' && reader.currentSymbol() == '{') {
                    state = PARSING_PARAM;
                    currentExpression = new StringBuffer();
                    reader.next();
                }
                else if(symbol=='\\' && reader.currentSymbol() == '$') {
                    buffer.append('$');
                    reader.next();
                }
                else {
                    buffer.append(symbol);
                }
            }
            else if (state ==  PARSING_PARAM) {
                if (symbol == '}') {
                    String expression = currentExpression.toString().trim();
                    Object value = getExpressionValue(expression, context);
                    if (value == null) {
                        value = "";
                    }
                    buffer.append(value.toString());
                    state = PARSING_TEXT;
                }
                else {
                    currentExpression.append(symbol);
                }
                
            }
        }
        return buffer.toString();
    }

    
    private Object getExpressionValue(String expression, Context context) {
        if (expression.matches("[a-zA-Z0-9..._]*")) {
            Object value = context.getValue(expression);
            if (value == null) {
                //Looking for value in properties
                
                if (properties != null) {
                    value = properties.getProperty(expression);
                }
                
                if (value != null) {
                    return value;
                }
                else return System.getProperty(expression, "");
            }
            return value;
        }
        else {
            return readJsExpression(expression, context);
        }
    }

    @SuppressWarnings("serial")
    private Object readJsExpression(String expression, Context context) {
        org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
        ScriptableObject scope = new ImporterTopLevel(cx);
        
        for (Map.Entry<String, Object> parameter : context.getParameters().entrySet()) {
            if (!conflictsWithFunctionNames(parameter.getKey())) {
                ScriptableObject.putProperty(scope, parameter.getKey(), parameter.getValue());
            }
        }
        
        if (jsFunctions != null) {
            scope.defineProperty("count", new BaseFunction() {
                @Override
                public Object call(org.mozilla.javascript.Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    if (args.length == 0 || !(args[0] instanceof String)) {
                        throw new IllegalArgumentException("Should take string argument");
                    }
                    return jsFunctions.count((String)args[0]);
                }
            }, ScriptableObject.DONTENUM);
        }
        
        Object returnedObject = cx.evaluateString(scope, expression, "<cmd>", 1, null);
        if (returnedObject instanceof Double) {
            return ((Double)returnedObject).intValue();
        }
        else if (returnedObject instanceof Float) {
            return ((Float)returnedObject).intValue();
        }
        return returnedObject;
    }

    private boolean conflictsWithFunctionNames(String name) {
        if (name.equals("count")) {
            return true;
        }
        return false;
    }

    public VarsParserJsFunctions getJsFunctions() {
        return jsFunctions;
    }

    public void setJsFunctions(VarsParserJsFunctions jsFunctions) {
        this.jsFunctions = jsFunctions;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

}