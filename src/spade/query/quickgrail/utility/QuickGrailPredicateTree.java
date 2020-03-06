/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.query.quickgrail.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;

import spade.query.quickgrail.parser.ParseExpression;
import spade.query.quickgrail.parser.ParseExpression.ExpressionType;
import spade.query.quickgrail.parser.ParseLiteral;
import spade.query.quickgrail.parser.ParseName;
import spade.query.quickgrail.parser.ParseOperation;
import spade.query.quickgrail.parser.ParseString;
import spade.query.quickgrail.parser.ParseVariable;
import spade.query.quickgrail.types.TypeID;
import spade.query.quickgrail.types.TypedValue;

public class QuickGrailPredicateTree{

	public static final String 
			BOOLEAN_OPERATOR_OR 	= "or",
			BOOLEAN_OPERATOR_AND 	= "and",
			BOOLEAN_OPERATOR_NOT 	= "not";
	
	public static final String 
			COMPARATOR_EQUAL1		= "=",
			COMPARATOR_EQUAL2		= "==",
			COMPARATOR_NOT_EQUAL1 	= "!=",
			COMPARATOR_NOT_EQUAL2	= "<>",
			COMPARATOR_LESS 		= "<",
			COMPARATOR_LESS_EQUAL	= "<=",
			COMPARATOR_GREATER 		= ">",
			COMPARATOR_GREATER_EQUAL= ">=",
			COMPARATOR_LIKE 		= "like",
			COMPARATOR_REGEX1 		= "~",
			COMPARATOR_REGEX2 		= "regex";
	
	private final static Set<String> validComparators = new HashSet<String>();
	static{
		validComparators.add(COMPARATOR_EQUAL1);	validComparators.add(COMPARATOR_EQUAL2);
		validComparators.add(COMPARATOR_NOT_EQUAL1);validComparators.add(COMPARATOR_NOT_EQUAL2);
		validComparators.add(COMPARATOR_LESS);		validComparators.add(COMPARATOR_LESS_EQUAL);
		validComparators.add(COMPARATOR_GREATER);	validComparators.add(COMPARATOR_GREATER_EQUAL);
		validComparators.add(COMPARATOR_LIKE);
		validComparators.add(COMPARATOR_REGEX1);	validComparators.add(COMPARATOR_REGEX2);
	}
	
	private final static Map<String, PredicateNode> predicates = new HashMap<String, PredicateNode>();
	
	public static PredicateNode lookupPredicateSymbol(String symbol){
		return predicates.get(symbol);
	}
	
	public static void setPredicateSymbol(String symbol, PredicateNode node){
		predicates.put(symbol, node);
	}
	
	public static PredicateNode resolveGraphPredicate(ParseExpression expression){
		ExpressionType expressionType = expression.getExpressionType();
		switch(expressionType){
			case kOperation:{
				ParseOperation predicate = (ParseOperation)expression;
				ParseString operator = predicate.getOperator();
				ArrayList<ParseExpression> operands = predicate.getOperands();
				switch(operator.getValue().toLowerCase()){
					case BOOLEAN_OPERATOR_OR:
					case BOOLEAN_OPERATOR_AND:{
						if(operands.size() != 2){
							throw new RuntimeException("Unexpected '"+operator.getValue()+"' operands count: " + operands.size() + ". Expected: " + 2);
						}
						PredicateNode result = new PredicateNode(operator.getValue().toLowerCase());
						result.left = (resolveGraphPredicate(operands.get(0)));
						result.right = (resolveGraphPredicate(operands.get(1)));
						return result;
					}
					case BOOLEAN_OPERATOR_NOT:{
						if(operands.size() != 1){
							throw new RuntimeException("Unexpected 'not' operands count: " + operands.size() + ". Expected: " + 1);
						}
						PredicateNode result = new PredicateNode(operator.getValue().toLowerCase());
						result.left = (resolveGraphPredicate(operands.get(0)));
						return result;
					}
					default:{
						final String comparatorString = operator.getValue().toLowerCase().trim();
						if(!validComparators.contains(comparatorString)){
							throw new RuntimeException("Unexpected comparator " + operator.getValue() + " at " + operator.getLocationString());
						}
						if(operands.size() != 2){
							throw new RuntimeException("Invalid number of operands at " + operator.getLocationString() + ": expected 2");
						}
						ParseExpression lhs = operands.get(0);
						ParseExpression rhs = operands.get(1);
						if(lhs.getExpressionType() != ParseExpression.ExpressionType.kName){
							throw new RuntimeException("Unexpected operand at " + lhs.getLocationString());
						}
						if(rhs.getExpressionType() != ParseExpression.ExpressionType.kLiteral){
							throw new RuntimeException("Unexpected operand at " + rhs.getLocationString());
						}
						String field = ((ParseName)lhs).getName().getValue();
						TypedValue literal = ((ParseLiteral)rhs).getLiteralValue();
						String value = literal.getType().printValueToString(literal.getValue());
						PredicateNode result = new PredicateNode(comparatorString);
						result.left = (new PredicateNode(field));
						result.right = (new PredicateNode(value));
						return result;
					}
				}
			}
			case kVariable:{
				ParseVariable parseVariable = (ParseVariable)expression;
				if(parseVariable.getType().getTypeID().equals(TypeID.kGraphPredicate)){
					String symbolName = parseVariable.getName().getValue();
					PredicateNode existingNode = lookupPredicateSymbol(symbolName);
					if(existingNode != null){
						return copy(existingNode);
					}else{
						throw new RuntimeException(
								"Cannot resolve Graph predicate variable " + symbolName + " at " + parseVariable.getLocationString());
					}
				}else{
					throw new RuntimeException("Illegal predicate variable assignment to type: " 
							+ parseVariable.getType().getTypeID().toString().substring(1));
				}
			}
			default:
				throw new RuntimeException("Unsupported expression type: " + expression.getExpressionType().name());
		}
	}
	
	private static PredicateNode copy(PredicateNode node){
		if(node == null){
			return null;
		}else{
			PredicateNode copy = new PredicateNode(node.value);
			copy.left = (copy(node.left));
			copy.right = (copy(node.right));
			return copy;
		}
	}
	
	public static byte[] serializeToBytes(PredicateNode node) throws Throwable{
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		try(ObjectOutputStream writer = new ObjectOutputStream(byteStream)){
			writer.writeObject(node);
			return byteStream.toByteArray();
		}
	}
	
	public static PredicateNode deserializeFromBytes(byte[] bytes) throws Throwable{
		ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
		try(ObjectInputStream reader = new ObjectInputStream(byteStream)){
			return (PredicateNode)reader.readObject();
		}
	}
	
	public static void testSer(PredicateNode node) throws Throwable{
		System.out.println("***Test1-start");
		System.out.println("original="+node);
		byte[] bytes = serializeToBytes(node);
		System.out.println("byteslength=" + bytes.length);
		PredicateNode newNode = deserializeFromBytes(bytes);
		System.out.println("copy="+newNode);
		System.out.println("***Test1-end");
	}
	
	public static void testSer2(PredicateNode node) throws Throwable{
		System.out.println("***Test2-start");
		System.out.println("original="+node);
		byte[] bytes = serializeToBytes(node);
		String stringFromBytes = Hex.encodeHexString(bytes);
		System.out.println("byteslengthoriginal=" + bytes.length);
		System.out.println("stringFromBytesLength=" + stringFromBytes.length());
		byte[] bytesFromString = Hex.decodeHex(stringFromBytes.toCharArray());
		System.out.println("byteslengthcopy=" + bytes.length);
		PredicateNode newNode = deserializeFromBytes(bytesFromString);
		System.out.println("copy="+newNode);
		System.out.println("***Test2-end");
	}
	
	public static void testSer3(PredicateNode node) throws Throwable{
		System.out.println("***Test3-start");
		System.out.println("original="+node);
		String serialized = serializePredicateNode(node);
		System.out.println("byteslength=" + serialized.length() + ", " + serialized);
		PredicateNode newNode = deserializePredicateNodePostfix(serialized);
		System.out.println("copy="+newNode);
		System.out.println("***Test3-end");
	}
	
	public static String serializePredicateNode(PredicateNode node){
		StringBuilder buffer = new StringBuilder(50);
		serializePredicateNodePostfix(node, buffer);
		return buffer.toString();
	}
	
	private static void serializePredicateNodePostfix(PredicateNode node, StringBuilder buffer){
		if(node.left != null){
			serializePredicateNodePostfix(node.left, buffer);
		}
		if(node.right != null){
			serializePredicateNodePostfix(node.right, buffer);
		}
		if(node.left == null && node.right == null){ // encode terminal nodes
			String newValue = "'" + node.value + "'";
			buffer.append(Hex.encodeHexString(newValue.getBytes()) + " ");
		}else{
			buffer.append(node.value + " ");
		}
	}
	
	public static PredicateNode deserializePredicateNodePostfix(String predicateNodeString) throws Throwable{
		LinkedList<PredicateNode> stack = new LinkedList<PredicateNode>();
		String[] tokens = predicateNodeString.split("\\s+");
		
		for(int i = 0; i < tokens.length; i++){
			String token = tokens[i];
			if(validComparators.contains(token)
					|| token.equals(BOOLEAN_OPERATOR_AND)
					|| token.equals(BOOLEAN_OPERATOR_OR)){ // must have two operands
				PredicateNode operator = new PredicateNode(token);
				PredicateNode right = stack.pop();
				PredicateNode left = stack.pop();
				operator.right = right;
				operator.left = left;
				stack.push(operator);
			}else if(token.equals(BOOLEAN_OPERATOR_NOT)){ // must have one operand
				PredicateNode operator = new PredicateNode(token);
				PredicateNode left = stack.pop();
				operator.left = left;
				stack.push(operator);
			}else{ // terminal node
				String newToken = new String(Hex.decodeHex(token.toCharArray()));
				newToken = newToken.substring(1, newToken.length() - 1);
				PredicateNode newNode = new PredicateNode(newToken);
				stack.push(newNode);
			}
		}
		
		return stack.pop();
	}
	
	public static class PredicateNode implements Serializable{
		
		private static final long serialVersionUID = -585074800550409280L;
		
		public final String value;
		private PredicateNode left, right;
		
		public PredicateNode(String value){
			this.value = value;
		}
		
		public PredicateNode getLeft(){
			return left;
		}
		
		public PredicateNode getRight(){
			return right;
		}
		
		private String toStringFileTree(){
			StringBuilder buffer = new StringBuilder(50);
			print(buffer, "", "");
			return buffer.toString();
		}
		
		public String toString(){
			return toStringFileTree();
		}
		
		public String toStringInOrder(){
			LinkedList<String> values = new LinkedList<String>();
			printToStringInOrder(values);
			
			String str = "";
			for(String value : values){
				if(validComparators.contains(value) || value.equals(BOOLEAN_OPERATOR_AND) || value.equals(BOOLEAN_OPERATOR_OR) || value.equals(BOOLEAN_OPERATOR_NOT)){
					str += " " + value + " ";
				}else{
					str += value;
				}
			}
			return str;
		}
		
		private void printToStringInOrder(LinkedList<String> values){
			boolean isNot = value.equals(BOOLEAN_OPERATOR_NOT);
			if(isNot){
				values.add(value);
			}
			
			if(left != null){
				values.add("(");
				left.printToStringInOrder(values);
			}
//			boolean printParens = validComparators.contains(value)  || value.equals(BOOLEAN_OPERATOR_AND)  || value.equals(BOOLEAN_OPERATOR_OR);
//			boolean printParens = left == null && right == null;
//			if(printParens){
//				str.append("(");
//			}
			
			if(!isNot){
				values.addLast(value);
			}
//			if(printParens){
//				str.append(")");
//			}
//			str.append(" ");
			if(right != null){
				right.printToStringInOrder(values);
				values.add(")");
			}
		}


		private void print(StringBuilder buffer, String prefix, String childrenPrefix){
			buffer.append(prefix);
			buffer.append(value);
			buffer.append('\n');
			if(left != null && right != null){
				left.print(buffer, childrenPrefix + "|-- ", childrenPrefix + "|   ");
				right.print(buffer, childrenPrefix + "|-- ", childrenPrefix + "    ");
			}else if(left != null && right == null){
				left.print(buffer, childrenPrefix + "|-- ", childrenPrefix + "    ");
			}else if(left == null && right != null){
				right.print(buffer, childrenPrefix + "|-- ", childrenPrefix + "    ");
			}
		}
	}
}