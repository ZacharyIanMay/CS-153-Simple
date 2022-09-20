/**
 * Executor class for a simple interpreter.
 * 
 * (c) 2020 by Ronald Mak
 * Department of Computer Science
 * San Jose State University
 *  
 * Additional work done by Team A: Jade Webb, Zachary May, Yinuo Tang, Ajita Srivastava
 * CS 153 Assignment #3
 * 
 */
package backend;

import java.util.ArrayList;
import java.util.HashSet;

import intermediate.*;
import static intermediate.Node.NodeType.*;

public class Executor
{
    private int lineNumber;
    private Symtab symtab;
    
    private static HashSet<Node.NodeType> singletons;
    private static HashSet<Node.NodeType> relationals;
    private static HashSet<Node.NodeType> arithmetics;
    private static HashSet<Node.NodeType> logicals;
    
    static
    {
        singletons  = new HashSet<Node.NodeType>();
        relationals = new HashSet<Node.NodeType>();
        arithmetics = new HashSet<Node.NodeType>();
        logicals = new HashSet<Node.NodeType>();
        
        singletons.add(VARIABLE);
        singletons.add(INTEGER_CONSTANT);
        singletons.add(REAL_CONSTANT);
        singletons.add(STRING_CONSTANT);
        
        relationals.add(EQ);
        relationals.add(NE);
        relationals.add(LT);
        relationals.add(LTE);
        relationals.add(GT);
        relationals.add(GTE);
        
        arithmetics.add(ADD);
        arithmetics.add(SUBTRACT);
        arithmetics.add(MULTIPLY);
        arithmetics.add(DIVIDE);
        
        logicals.add(AND);
        logicals.add(OR);
        logicals.add(NOT);
        
    }
    
    public Executor(Symtab symtab)
    {
        this.symtab = symtab;
    }
    
    public Object visit(Node node)
    {
        switch (node.type)
        {
            case PROGRAM :  return visitProgram(node);
            
            case COMPOUND : 
            case ASSIGN :   
            case LOOP : 
            case WRITE :
            case WRITELN :  return visitStatement(node);
            
            case TEST:      return visitTest(node);
            
            case IFNODE:    return visitIfNode(node);
            case SELECT:    return visitCaseNode(node);
            
            default :       return visitExpression(node);
        }
    }
    
    private Object visitProgram(Node programNode)
    {
        Node compoundNode = programNode.children.get(0);
        return visit(compoundNode);
    }
    
    private Object visitStatement(Node statementNode)
    {
        lineNumber = statementNode.lineNumber;
        
        switch (statementNode.type)
        {
            case COMPOUND :  return visitCompound(statementNode);
            case ASSIGN :    return visitAssign(statementNode);
            case LOOP :      return visitLoop(statementNode);
            case WRITE :     return visitWrite(statementNode);
            case WRITELN :   return visitWriteln(statementNode);
            case IFNODE:     return visitIfNode(statementNode);
            case SELECT:     return visitCaseNode(statementNode);
            
            default :        return null;
        }
    }
    
    private Object visitCompound(Node compoundNode)
    {
        for (Node statementNode : compoundNode.children) visit(statementNode);
        
        return null;
    }
    
    private Object visitAssign(Node assignNode)
    {
        Node lhs = assignNode.children.get(0);
        Node rhs = assignNode.children.get(1);
        
        // Evaluate the right-hand-side expression;
        Double value = (Double) visit(rhs);
        
        // Store the value into the variable's symbol table entry.
        String variableName = lhs.text;
        SymtabEntry variableId = symtab.lookup(variableName);
        variableId.setValue(value);
        
        return null;
    }
    
    private Object visitLoop(Node loopNode)
    {        
        boolean b = false;
        do
        {
            for (Node node : loopNode.children)
            {
                Object value = visit(node);  // statement or test
                
                // Evaluate the test condition. Stop looping if true.
                b = (node.type == TEST) && ((boolean) value);
                if (b) break;
            }
        } while (!b);
        
        return null;
    }
    
    private Object visitIfNode(Node ifNode)
    {        
        if ((Boolean) visit(ifNode.children.get(0))) 
        {
        	visit(ifNode.children.get(1));
        } else if (ifNode.children.size() > 2) 
        {
        	visit(ifNode.children.get(2));
        }
        return null;
    }
    
    private Object visitCaseNode(Node caseNode)
    {   
    	
    	Double value = (Double) visit(caseNode.children.get(0));
    	
    	for (int i = 1; i < caseNode.children.size(); i++)
        {
            Node branch = caseNode.children.get(i);
            
            Node constants = branch.children.get(0);
            
            for (int j = 0; j < constants.children.size(); j++) 
            {
            	if (((Double) visit(constants.children.get(j))).equals(value)) 
                {
                	return visit(branch.children.get(1));
                }
            }
        }
    	return null;
    }
    
    private Object visitTest(Node testNode)
    {
        return (Boolean) visit(testNode.children.get(0));
    }
    
    private Object visitWrite(Node writeNode)
    {
        printValue(writeNode.children);
        return null;
    }
    
    private Object visitWriteln(Node writelnNode)
    {
        if (writelnNode.children.size() > 0) printValue(writelnNode.children);
        System.out.println();
        
        return null;
    }

    private void printValue(ArrayList<Node> children)
    {
        long fieldWidth    = -1;
        long decimalPlaces = 0;
        
        // Use any specified field width and count of decimal places.
        if (children.size() > 1)
        {
            double fw = (Double) visit(children.get(1));
            fieldWidth = (long) fw;
            
            if (children.size() > 2) 
            {
                double dp = (Double) visit(children.get(2));
                decimalPlaces = (long) dp;
            }
        }
        
        // Print the value with a format.
        Node valueNode = children.get(0);
        if (valueNode.type == VARIABLE)
        {
            String format = "%";
            if (fieldWidth >= 0)    format += fieldWidth;
            if (decimalPlaces >= 0) format += "." + decimalPlaces;
            format += "f";
            
            Double value = (Double) visit(valueNode);
            System.out.printf(format, value);
        }
        else  // node type STRING_CONSTANT
        {
            String format = "%";
            if (fieldWidth > 0) format += fieldWidth;
            format += "s";
            
            String value = (String) visit(valueNode);
            System.out.printf(format, value);
        }
    }

    private Object visitExpression(Node expressionNode)
    {
        // Single-operand expressions.
        if (singletons.contains(expressionNode.type))
        {
            switch (expressionNode.type)
            {
                case VARIABLE         : return visitVariable(expressionNode);
                case INTEGER_CONSTANT : return visitIntegerConstant(expressionNode);
                case REAL_CONSTANT    : return visitRealConstant(expressionNode);
                case STRING_CONSTANT  : return visitStringConstant(expressionNode);
                
                default: return null;
            }
        }
        
        // Relational expressions.
        if (relationals.contains(expressionNode.type))
        {
        	 // Binary expressions.
            double value1 = (Double) visit(expressionNode.children.get(0));
            double value2 = (Double) visit(expressionNode.children.get(1));
            
            boolean value = false;
            
            switch (expressionNode.type)
            {
                case EQ : value = value1 == value2; break;
                case NE : value = value1 != value2; break;
                case LT : value = value1 <  value2; break;
                case LTE : value = value1 <= value2; break;
                case GT : value = value1 > value2; break;
                case GTE : value = value1 >= value2; break;
                
                default : break;
            }
            
            return value;
        }
        
        // Arithmetic expressions.
        if (arithmetics.contains(expressionNode.type))
        {
        	 // Binary expressions.
            double value1 = (Double) visit(expressionNode.children.get(0));
            double value2 = (Double) visit(expressionNode.children.get(1));
            
        	double value = 0.0;
        
        	switch (expressionNode.type)
        	{
        		case ADD :      value = value1 + value2; break;
        		case SUBTRACT : value = value1 - value2; break;
        		case MULTIPLY : value = value1 * value2; break;
                
        		case DIVIDE :
        		{
        			if (value2 != 0.0) value = value1/value2;
        			else
        			{
        				runtimeError(expressionNode, "Division by zero");
        				return 0.0;
        			}
        			
        			break;
        		}
        		
        		default : break;
        	}
        	
        	return value;
        }

        // Logical expressions.
        if (logicals.contains(expressionNode.type))
        {
        	// Binary expressions.
            boolean value1 = (Boolean) visit(expressionNode.children.get(0));
            
        	boolean value = false;
        
        	switch (expressionNode.type)
        	{
        		case AND :      boolean value2AND = (Boolean) visit(expressionNode.children.get(1));
        						value = value1 && value2AND; break;
        		case OR : 		boolean value2OR = (Boolean) visit(expressionNode.children.get(1));
        						value = value1 || value2OR; break;
        		case NOT : 		value = !value1; break;
        		
        		default : break;
        	}
        	
        	return value;
        }

       return null;
    }
    
    private Object visitVariable(Node variableNode)
    {
        // Obtain the variable's value from its symbol table entry.
        String variableName = variableNode.text;
        SymtabEntry variableId = symtab.lookup(variableName);
        Double value = variableId.getValue();
        
        return value;
    }
    
    private Object visitIntegerConstant(Node integerConstantNode)
    {
        long value = (Long) integerConstantNode.value;
        return (double) value;
    }
    
    private Object visitRealConstant(Node realConstantNode)
    {
        return (Double) realConstantNode.value;
    }
    
    private Object visitStringConstant(Node stringConstantNode)
    {
        return (String) stringConstantNode.value;
    }

    private void runtimeError(Node node, String message)
    {
        System.out.printf("RUNTIME ERROR at line %d: %s: %s\n", 
                          lineNumber, message, node.text);
        System.exit(-2);
    }
}
