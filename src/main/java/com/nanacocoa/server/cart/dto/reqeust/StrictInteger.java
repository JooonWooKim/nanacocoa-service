package com.nanacocoa.server.cart.dto.reqeust;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
/** 수량의 소수·문자열 변환을 허용하지 않는다. */
public class StrictInteger extends StdDeserializer<Integer> {
    public StrictInteger(){super(Integer.class);}
    @Override public Integer deserialize(JsonParser parser,DeserializationContext context)throws IOException{
        if(!parser.hasToken(JsonToken.VALUE_NUMBER_INT))return (Integer)context.handleUnexpectedToken(Integer.class,parser);
        return parser.getIntValue();
    }
}
