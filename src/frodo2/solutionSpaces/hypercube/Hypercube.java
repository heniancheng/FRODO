/*
FRODO: a FRamework for Open/Distributed Optimization
Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek

FRODO is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

FRODO is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.


How to contact the authors: 
<http://frodo2.sourceforge.net/>
*/

/**
 * @author Ouaret Nacereddine, Thomas Leaute, Radoslaw Szymanek
 */

package frodo2.solutionSpaces.hypercube;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * The class representing a hypercube
 * @author  Ouaret Nacereddine, Thomas Leaute, Radoslaw Szymanek, Stephane Rabie, Brammert Ottens
 * @param  < V  >  type used for the variable values
 * @param  < U  >  type used for the utility values
 * @todo When a method makes assumptions on the input parameters (for instance, the order of the variables), use asserts to check 
 * if these assumptions hold and document them in the method Doxygen comment. 
 */
public class Hypercube< V extends Addable<V>, U extends Addable<U> > 
extends HypercubeLimited<V, U, U> implements UtilitySolutionSpace<V, U> {
	
	/**Empty Hypercube constructor*/
	public Hypercube() { }
	
	/**Construct a new Hypercube with provided variables names, the domains of these variables and the utility values
	 * @param variables_order     the array containing the variables names ordered according to their order in the hypercube
	 * @param variables_domains   the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utility_values      the utility values contained in a one-dimensional array. there should be a utility value for each 
	 *                            possible combination of values that the variables may take.
	 * @param steps_hashmaps      steps allowing faster access to utilities of a given assignment.
	 *
	 * @warning variables_domains parameter needs to be sorted in ascending order.
	 * @warning utility_values needs to be properly ordered, the first utility corresponds to the 
	 * assignment in which each variable is assigned its smallest value.
	 */
	@SuppressWarnings("unchecked")
	protected Hypercube ( String[] variables_order, V[][] variables_domains, U[] utility_values, 
					   HashMap< V, Integer >[] steps_hashmaps) {
		
		assert variables_order.length > 0  : "A hypercube must contain at least one variable";
		
		assert variables_order.length == variables_domains.length : "A hypercube must specify a domain for each of its variables";
		
		this.variables = variables_order;
		this.domains = variables_domains;
		this.values = utility_values;
		this.number_of_utility_values = this.values.length;
		this.steps_hashmaps = steps_hashmaps;
		this.classOfV = (Class<V>) variables_domains.getClass().getComponentType().getComponentType();
	    this.assignment = (V[]) Array.newInstance(this.classOfV, this.variables.length);
		
	}

	
	
	/**Construct a new Hypercube with provided variables names, the domains of this variables and the utility values
	 * @param variables_order 		the array containing the variables names ordered according to their order in the hypercube
	 * @param variables_domains 	the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utility_values 		the utility values contained in a multi-dimensional array. there should be a utility value for each 
	 * 								possible combination of values that the variables may take.
	 * @param infeasibleUtil 		-INF if we are maximizing, +INF if we are minimizing
	 * @todo Test this constructor. 
	 */
	@SuppressWarnings("unchecked")
	public Hypercube( String[] variables_order, V[][] variables_domains, Object[] utility_values, U infeasibleUtil ) {

		assert  variables_order.length > 0  : "A hypercube must contain at least one variable";
		
		assert variables_order.length == variables_domains.length : "A hypercube must contain exactly one domain for each of its variables";
		
		assert notEmptyDomains(variables_domains) : "Must provide a non-empty domain for each variable of the hypercube";
		
		this.variables = variables_order.clone();
		this.domains = variables_domains.clone();
		super.classOfV = (Class<V>) variables_domains.getClass().getComponentType().getComponentType();
		this.infeasibleUtil = infeasibleUtil;
		
		//number of variables in the hypercube
		int number_of_variables = variables.length;
	    super.assignment = (V[]) Array.newInstance(super.classOfV, number_of_variables);
		
		//compute the number of utility values in the hypercube
		int number_of_utility_values = 1;
		for(V[] domain:domains) {
			assert Math.log((double) number_of_utility_values) + Math.log((double) domain.length) < Math.log(Integer.MAX_VALUE) : 
				"Size of utility array too big for an int";
			number_of_utility_values *= domain.length;
		}
		this.number_of_utility_values = number_of_utility_values;
		
		
		//transform the multi-dimension utility values array into a one-dimension array
		/// @bug Extract the type used for utility values from the multi-dimensional array utility_values
		values = (U[]) new Addable[ number_of_utility_values ];
		//this array will contain the indexes used to go through the provided utility values array. no need for an index for the last variable
		int[] indexes = new int[ number_of_variables - 1 ];
		
		int index_to_increment = number_of_variables - 2;
		int index;
		int domain_size;
		Object[] values_tmp;
		int number_of_loops = number_of_utility_values/domains[domains.length-1].length;
		for( int i = 0; i < number_of_loops; i++ ) {
			
			values_tmp = utility_values;
			
			//get the utility values corresponding to the current array of indexes
			for( int j = 0; j < number_of_variables - 1; j++ ) {
				values_tmp = (Object[]) values_tmp[ indexes[j] ];
			}
			
			//insert the utility values into the utility values array of the hypercube
			System.arraycopy( values_tmp, 0, values, i * domains[ number_of_variables - 1 ].length, values_tmp.length );
			
			//increment the indexes array
			for( int j=number_of_variables - 2; j >= 0; j-- ) {
				index = indexes[j];
				domain_size = domains[j].length;
				
				//increment this index or not
				if( index_to_increment == j ){
					index = ( index + 1 ) % domain_size;
					indexes[ j ] = index;
					
					//when a whole loop is done over all the possible values of the current index, increment also the next
					//index
					if(index == 0) 
						index_to_increment--; 
					else index_to_increment = number_of_variables - 2;
				}
			}
		}
		
		//fill the steps hashmaps
		setStepsHashmaps();
	}
	
	/**Construct a new Hypercube with provided variables names, the domains of these variables and the utility values
	 * @param variables_order 		the array containing the variables names ordered according to their order in the hypercube
	 * @param variables_domains 	the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utility_values 		the utility values contained in a one-dimensional array. there should be a utility value for each 
	 * 								possible combination of values that the variables may take.
	 * @param infeasibleUtil 		-INF if we are maximizing, +INF if we are minimizing
	 * @warning variables_domains parameter needs to be sorted in ascending order.
	 * @warning utility_values needs to be properly ordered, the first utility corresponds to the 
	 * assignment in which each variable is assigned its smallest value.
	 */
	public Hypercube( String[] variables_order, V[][] variables_domains, U[] utility_values, U infeasibleUtil ) {
		super(variables_order, variables_domains, utility_values, infeasibleUtil);
	}
	
	/**Construct a new Hypercube with provided variables names, the domains of these variables and the utility values
	 * @param variables_order 		the array containing the variables names ordered according to their order in the hypercube
	 * @param variables_domains 	the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utility_values 		the utility values contained in a one-dimensional array. there should be a utility value for each 
	 * 								possible combination of values that the variables may take.
	 * @param infeasibleUtil 		-INF if we are maximizing, +INF if we are minimizing
	 * @param problem 				the problem to be notified of constraint checks
	 * @warning variables_domains parameter needs to be sorted in ascending order.
	 * @warning utility_values needs to be properly ordered, the first utility corresponds to the 
	 * assignment in which each variable is assigned its smallest value.
	 */
	public Hypercube( String[] variables_order, V[][] variables_domains, U[] utility_values, U infeasibleUtil, ProblemInterface<V, U> problem ) {
		super(variables_order, variables_domains, utility_values, infeasibleUtil, problem);
	}
	
	/**Construct a new Hypercube with the variables names, the domains of this variables and the utility values stored in the provided 
	 * XML file
	 * @param file the name of the XML file containing the needed information. the file name may also contain the path to that XML file
	 */
	@SuppressWarnings("unchecked")
	public Hypercube( String file ) {
		Class<?> c;
		SAXBuilder builder = new SAXBuilder();
		
	    try {
	      Document document = builder.build( file );
	      //extract the root element of the XML file
	      Element root = document.getRootElement();
	      
	      //start creating the hypercube 
	      
	      //extract the element containing the domains of the variables
	      Element domains_element = root.getChild( "domains" );
	      //get the list of elements containing the information about the variables (order, domain)
	      List< Element > variables_elements = domains_element.getChildren( "variable" );
	      //number of variables in the hypercube
	      int number_of_variables = variables_elements.size();
	      
	      variables = new String[number_of_variables];
	      /// @todo We apparently cannot use Array.newInstance here because the file format allows different value types for different variables
	      domains = (V[][]) new Addable[number_of_variables][];
	      
	      Element variable_element;
	      //this integer is used to compute the number of utility values
	      int number_utility_values = 1;
	      for( int i = 0; i < number_of_variables; i++ ) {
	    	  //the ith "variable" element
	    	  variable_element = variables_elements.get( i );
	    	  
	    	  //extract the order of the variable
	    	  int order = variable_element.getAttribute( "order" ).getIntValue();
	    	  //extract the name of the variable and insert it in the array of variables according to its order
	    	  variables[ order ] = variable_element.getAttribute( "name" ).getValue();
	    	  //extract the domain as a string and split it into tokens using a StringTokenizer
	    	  StringTokenizer domain = new StringTokenizer( variable_element.getText() );
	    	  //the size of the domain of the variable
	    	  int domain_size = domain.countTokens();
	    	  
	    	  //create a Class object from the type of the variable to use it later to instantiate objects to fill 
	    	  //the domains array (domains)
	    	  c = Class.forName( variable_element.getAttributeValue( "type" ) );
	    	  if (super.classOfV == null) 
	    		  super.classOfV = (Class<V>) c;
	    	  V valInstance = (V) c.newInstance();
	    	  Method valFromString = c.getMethod("fromString", String.class);
	    	  //Note: we assume that the class representing the type of the variable contains a constructor that 
	    	  //takes a String object as a parameter
	    	 
	    	  //fill the domain of the variable in the domains array
	    	  domains[i] = (V[]) Array.newInstance(c, domain_size);
	    	  for( int j = 0; j < domain_size; j++) {
	    		  domains[i][j] = (V) valFromString.invoke(valInstance, domain.nextToken());
	    	  }
			  assert Math.log((double) number_utility_values) + Math.log((double) domain_size) < Math.log(Integer.MAX_VALUE) : 
					"Size of utility array too big for an int";
	    	  number_utility_values *= domain_size;
	      }
	      
	      super.assignment = (V[]) Array.newInstance(super.classOfV, number_of_variables);
	      
	      //extract the element containing the utility values
	      Element values_element = root.getChild( "utility_values" );
	      //construct a Class object that will be used later to instantiate objects to fill the utility values array
	      c = Class.forName(values_element.getAttributeValue( "type") );
    	  U utilInstance = (U) c.newInstance();
    	  this.infeasibleUtil = utilInstance.getMinInfinity(); /// @todo Actually, the file doesn't say whether it should be -INF or +INF
    	  Method utilFromString = c.getMethod("fromString", String.class);
	      values = (U[]) Array.newInstance(c, number_utility_values);
	      this.number_of_utility_values = number_utility_values;
	      //extract the list of elements contains information about the utility values
	      variables_elements = values_element.getChildren( "variable" );
	     
	      int index = 0;
	      
	      if( variables_elements.size() > 0 )
		      for( Element next_element : variables_elements ) {
		    	  //extract the "variable" element that contains the utility values
		    	  while( next_element.getChild( "variable" ) != null )
		    		  next_element = next_element.getChild( "variable" );
		    		  
		    	  //extract the utility values as a string and split it into tokens using a StringTokenizer
		    	  StringTokenizer utility_values = new StringTokenizer( next_element.getText() );
		    	  
		    	  String value = utility_values.nextToken(); 
		    	  values[ index ] =  (U) utilFromString.invoke(utilInstance, value);
	    		  index++;
		    	  //put the extracted utility values in the array of utility values
		    	  while( utility_values.hasMoreTokens() ) {
		    		  //Note: we assume that the class representing the type of the utility value contains a constructor that 
			    	  //takes a String object as parameter
		    		  value = utility_values.nextToken();
			    	  values[ index ] =  (U) utilFromString.invoke(utilInstance, value);
		    		  index++;
		    	  } 
		      }
	      else{
	    	  //case when there is only one variable in the hypercube
	    	  
	    	  //extract the utility values as a string and split it into tokens using a StringTokenizer
	    	  StringTokenizer utility_values = new StringTokenizer( values_element.getText() );
	    	  
	    	  //put the extracted utility values in the array of utility values
	    	  while( utility_values.hasMoreTokens() ) {
	    		  //Note: we assume that the class representing the type of the utility value contains a constructor that 
		    	  //takes a String object as parameter
	    		  values[ index ] =  (U) utilFromString.invoke(utilInstance, utility_values.nextToken());
	    		  index++;
	    	  } 
	      }
	      
	      setStepsHashmaps();
	    }
	    catch (Exception e) {
			System.err.println(e.getMessage());
		} 
	}
	
	/** @see UtilitySolutionSpace#setProblem(ProblemInterface) */
	public void setProblem(ProblemInterface<V, U> problem) {
		super.problem = problem;
	}
	
	/** Creates an XML file from the hypercube
	 * @param file the name of the XML file in which the hypercube will be saved
	 */
	@SuppressWarnings("unchecked")
	public void saveAsXML( String file ) {
		//create the root element
		Element root = new Element( "hypercube" );
		
		//
		//                    DOMAINS PART OF THE XML FILE
		//
		
		//create the element that will contain the domains of the variables
		Element domains_element = new Element( "domains" );
		//the number of variables in the hypercube
		int number_of_variables = variables.length;
		
		Element variable_element;
		V[] domain; int domain_size; 
		StringBuilder v;
		
		//for each variable of the hypercube
		for( int i = 0; i < number_of_variables; i++){
			//the domain of the ith variable of the hypercube
			domain = domains[ i ];
			//the size of the variable domain
			domain_size = domain.length;
			//create an element for the variable
			variable_element = new Element( "variable" );
			//attach the name of the variable as an attribute
			variable_element.setAttribute( "name", variables[ i ] );
			//attach the type of the variable as an attribute
			variable_element.setAttribute( "type", domains[ i ][ 0 ].getClass().getName() );
			//attach the order of the variable as an attribute
			variable_element.setAttribute("order", String.valueOf( i ) );
			
			//create a String object containing the domain of the domain
			v = new StringBuilder();
			for( int j = 0; j < domain_size; j++){
				v.append( domain[ j ] );
				v.append( " " );
			}
			
			//attach the domain of the variable to the variable's element
			variable_element.setText( v.toString() );
			//attach the element that contains the domain of the variable to the element containing the domains as a child
			domains_element.addContent( variable_element );
		}
		//attach the element containing the domains to the root element as a child
		root.addContent( domains_element );
		
		//
		//        UTILITY VALUES PART OF THE XML FILE
		//
		
		//number of the utility values of the hypercube
		int number_of_values = number_of_utility_values;
		//create the element that will contain the utility values
		Element values_element = new Element( "utility_values" );
		//attach the type of the utility values as an attribute
		
		Class<U> c = (Class<U>) values[0].getClass();
		if(c.getDeclaringClass() == null) {
			values_element.setAttribute( "type", values[0].getClass().getName() );	
		} else {
			values_element.setAttribute( "type", values[0].getClass().getDeclaringClass().getName() );
		}
		
		
		/*
		 * What we need to do now is: for every combination of possible values of the variables, we find the corresponding
		 * utility value and store both of the informations in the XML file.
		 */
		
		//an array of indexes used to go through the array of domains
		int[] indexes = new int[ number_of_variables ];
		//the index of the index to increment
		int index_to_increment = number_of_variables - 2;
		
		//we do not need an element for the last variable
		int number_of_loops = number_of_values / domains[ number_of_variables - 1 ].length;
		int index;
		for( int i = 0; i < number_of_loops; i++ ) {
			
			//the next array of possible values
			for(int j = number_of_variables - 2; j >= 0; j--){
				index = indexes[j];
				domain = domains[j];
				this.assignment[j] = domain[index];
				if(index_to_increment == j){
					index = (index+1) % domain.length;
					indexes[j] = index;
					
					// when a whole loop is done over all the domain of the variable increment also the next variable which 
					//is previous to this one in order
					if(index == 0) 
						index_to_increment--; 
					else index_to_increment = number_of_variables - 2;
				}
			}
			
			//construct the elements containing the next array of possible values
			Element next_variable_element;
			variable_element = values_element;
			for(int j = 0; j < number_of_variables - 1; j++){
				//create an element for the variable
				next_variable_element = new Element( "variable" );
				//attach the name of the variable as an attribute to the element
				next_variable_element.setAttribute( "name", variables[ j ] );
				//attach the value of the variable as an attribute to the element
				next_variable_element.setAttribute( "value", this.assignment[ j ].toString() );
				

				//each of the other element is attached to the element representing the previous element
				variable_element.addContent( next_variable_element );
				variable_element = next_variable_element;
			}
			
			//create a String that contains the utility values corresponding to the first "number_of_variables-1" variables taking
			//fixed values contained in the "variables_values" array and the last variable taking values over its whole domain
			StringBuilder utility_values = new StringBuilder();
			
			//loop over all possible value of the last variable
			domain = domains[ number_of_variables - 1 ];
			
			for( V variable_value : domain ) {
				this.assignment[ number_of_variables - 1 ] = variable_value;
			    
			    //get the corresponding utility value and add it to the String
				utility_values.append( getUtility( this.assignment ) );
				utility_values.append( " " );
			}
			
			//attach the builded String to the last "variable" element
			variable_element.setText( utility_values.toString() );
		}
		//attach the "utility_values" element containing the utility values to the root element
		root.addContent( values_element );
		
		Document document = new Document( root );
	    try {
	      XMLOutputter output = new XMLOutputter( Format.getPrettyFormat() );
		  output.output( document, new FileOutputStream( file ) );
	    }
	    catch (IOException e) {
	      System.err.println( e.getMessage() );
	    }
	}
	
	/** 
	 * @see BasicHypercube#getUtility(java.lang.String[], V[]) 
	 * @warning returns the utility zero if not all variables in the space have been assigned values
	 */
	@Override
	public U getUtility( String[] variables_names, V[] variables_values ) {
		
		U out = super.getUtility(variables_names, variables_values);
		
		if (out == null) // some variables in the space remain unassigned
			return this.infeasibleUtil.getZero();
		else 
			return out;
	}
	
	/** @see BasicHypercube#getUtility(java.util.Map) */
	@Override
	public U getUtility(Map<String, V> assignments) {
		
		U out = super.getUtility(assignments);
		
		if (out == null) // some variables in the space remain unassigned
			return this.infeasibleUtil.getZero();
		else 
			return out;
	}
	
	/** Returns a Hypercube object obtained by joining this hypercube with the one provided as a parameter
	 * @param utilitySpace 		the hypercube to join this one with
	 * @param outputVars 		the desired order in the output hypercube
	 * @param addition 			\c true if utilities should be added, \c false if they should be multiplied
	 * @param minNCCCs 			whether to optimize runtime or NCCC count
	 * @return Hypercube object obtained by joining this hypercube with the one provided as a parameter
	 * @warning Assumes that each variable in \a outputVars is contained in either of the two hypercubes. 
	 * @author Thomas Leaute 
	 */
	@SuppressWarnings("unchecked")
	protected UtilitySolutionSpace< V, U > join( UtilitySolutionSpace< V, U > utilitySpace, String[] outputVars, final boolean addition, final boolean minNCCCs ) {
		
		assert sub(outputVars, union(this.variables, utilitySpace.getVariables())).length == 0 : 
			"Not all variables in " + Arrays.asList(outputVars) + " are contained in the input hypercubes: " + this + "\n" + utilitySpace;
		
		if(utilitySpace == NullHypercube.NULL)
			return NullHypercube.NULL;
		
		// Compute the domains of the output hypercube and the number of utilities
		Class<?> domClass = this.domains.getClass().getComponentType();
		int nbrOutputVars = outputVars.length;
		V[][] outputDomains = (V[][]) Array.newInstance(domClass, nbrOutputVars);
		long nbrOutputUtils = 1;
		for (int i = 0; i < nbrOutputVars; i++) {
			String var = outputVars[i];
			
			// Look up the domain in the two input hypercubes
			V[] dom = this.getDomain(var);
			V[] dom2 = utilitySpace.getDomain(var);
			
			// Compute the intersection if necessary
			if (dom == null) {
				dom = dom2;
			} else if (dom2 != null) { // the variable is in both hypercubes
				dom = intersection(dom, dom2);
				if( dom == null ) return NullHypercube.NULL;
			}
			
			outputDomains[i] = dom.clone();

			assert Math.log(nbrOutputUtils) + Math.log(dom.length) < Math.log(Long.MAX_VALUE) : "Too many solutions in a space";
			nbrOutputUtils *= dom.length;
		}
		
		if (minNCCCs) { // minimize the NCCC count, at the expense of runtime
			
			// Create the output hypercube, with an initially empty array of utilities
			assert nbrOutputUtils < Integer.MAX_VALUE : "A Hypercube cannot contain more than 2^31-1 solutions";
			U[] outputUtils = (U[]) Array.newInstance(this.values.getClass().getComponentType(), (int) nbrOutputUtils);
			Hypercube<V, U> out = this.newInstance((String[]) outputVars.clone(), outputDomains, outputUtils, this.infeasibleUtil );
			
			// Initialize the output utilities with the caller hypercube's utilities
			UtilitySolutionSpace.Iterator<V, U> outIter = out.iterator(this.variables, this.domains);
			UtilitySolutionSpace.Iterator<V, U> myIter = this.iterator(this.variables, outIter.getDomains());
			long utilFactor = outIter.getNbrSolutions() / myIter.getNbrSolutions();
			while (myIter.hasNext()) {
				U util = myIter.nextUtility();
				for (long j = 0; j < utilFactor; j++) {
					outIter.nextSolution();
					outIter.setCurrentUtility(util);
				}
			}

			// Add/multiply to the output utilities the input hypercube's utilities
			outIter = out.iterator(utilitySpace.getVariables(), utilitySpace.getDomains());
			myIter = utilitySpace.iterator(utilitySpace.getVariables(), outIter.getDomains());
			utilFactor = outIter.getNbrSolutions() / myIter.getNbrSolutions();
			while (myIter.hasNext()) {
				U util = myIter.nextUtility();
				for (long j = 0; j < utilFactor; j++) {
					U oldUtil = outIter.nextUtility();
					if (addition) 
						outIter.setCurrentUtility(oldUtil.add(util));
					else 
						outIter.setCurrentUtility(oldUtil.multiply(util));
				}
			}
			
			return out;
			
		} else // optimize runtime, not caring about the NCCC count
			return new JoinOutputHypercube<V, U> (this, utilitySpace, outputVars, outputDomains, addition, this.infeasibleUtil, nbrOutputUtils);
	}
	
	/** Computes the join of this hypercube with the input hypercube
	 * @param space 		input hypercube
	 * @param addition 		\c true if utilities should be added, \c false if they should be multiplied
	 * @param minNCCCs 		whether to optimize runtime or NCCC count
	 * @return join of the two hypercubes
	 * @note The variable order used is the one returned by the \a union() method.
	 * @see Hypercube#union(String[], String[])
	 * @author Thomas Leaute
	 */
	public UtilitySolutionSpace< V, U > join( UtilitySolutionSpace< V, U > space, boolean addition, boolean minNCCCs ) {
		return this.join(space, union(this.variables, space.getVariables()), addition, minNCCCs);
	}
	
	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<V, U> join(UtilitySolutionSpace<V, U> space, String[] total_variables) {
		
		if (! this.knows(space.getClass()) && space.knows(this.getClass())) 
			return space.join(this, total_variables);

		// Reduce total_variables to only contain relevant variables
		ArrayList<String> outputVars = new ArrayList<String> (total_variables.length);
		for (String var : total_variables) 
			if (space.getDomain(var) != null || this.getDomain(var) != null) 
				outputVars.add(var);
		
		return this.join(space, outputVars.toArray(new String [outputVars.size()]), true, false);
	}

	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace) */
	@Override
	public UtilitySolutionSpace<V, U> join(UtilitySolutionSpace<V, U> space) {
		
		if (! this.knows(space.getClass()) && space.knows(this.getClass())) 
			return space.join(this);
		
		return this.join(space, true, false);
	}

	/** @see UtilitySolutionSpace#joinMinNCCCs(UtilitySolutionSpace) */
	public UtilitySolutionSpace<V, U> joinMinNCCCs (UtilitySolutionSpace<V, U> space) {
		
		if (! this.knows(space.getClass()) && space.knows(this.getClass())) 
			return space.join(this);
		
		return this.join(space, true, true);
	}

	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<V, U> join(UtilitySolutionSpace<V, U>[] spaces) {
		
		if (spaces.length == 0) 
			return this;
		
		return this.join(spaces, true, false);
	}

	/** @see UtilitySolutionSpace#joinMinNCCCs(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<V, U> joinMinNCCCs(UtilitySolutionSpace<V, U>[] spaces) {
		
		if (spaces.length == 0) 
			return this.resolve();
		
		return this.join(spaces, true, true);
	}

	/** The join method 
	 * @param spaces 		input spaces
	 * @param addition 		\c true if utilities should be added, \c false if they should be multiplied
	 * @param minNCCCs 		whether to optimize runtime or NCCC count
	 * @return the joint hypercube
	 */
	@SuppressWarnings("unchecked")
	protected UtilitySolutionSpace<V, U> join(UtilitySolutionSpace<V, U>[] spaces, final boolean addition, final boolean minNCCCs) {
		
		// If I don't know one of the input spaces, but it knows me, then call the join on it
		int number_of_hypercubes = spaces.length;
		Class<?> myClass = this.getClass();
		for (int i = 0; i < number_of_hypercubes; i++) {
			UtilitySolutionSpace<V, U> space = spaces[i];
			if (! this.knows(space.getClass()) && space.knows(myClass)) {
				spaces[i] = this;
				return space.join(spaces);
			}
		}
				
		// Compute the intersections of all domains
		HashMap< String, V[] > doms = new HashMap< String, V[] > ();
		
		// Go through all my variables
		int nbrVars = this.variables.length;
		for (int i = 0; i < nbrVars; i++) 
			doms.put(variables[i], this.domains[i]);
		
		// Go through all input spaces
		int nbrSpaces = spaces.length;
		for (int i = 0; i < nbrSpaces; i++) {
			UtilitySolutionSpace<V, U> space = spaces[i];
			
			if (space == Hypercube.NullHypercube.NULL) 
				return Hypercube.NullHypercube.NULL;
			
			// Go through all variables in the space
			nbrVars = space.getNumberOfVariables();
			for (int j = 0; j < nbrVars; j++) {
				String var = space.getVariable(j);
				
				// Intersect with the current domain for this variable
				V[] dom = doms.get(var);
				if (dom != null) { // this variable is already known
					
					// Compute the intersection of the domains
					dom = intersection(doms.get(var), space.getDomain(j));
					
					if (dom == null) // empty intersection
						return Hypercube.NullHypercube.NULL;
					
				} else // unknown variable
					dom = space.getDomain(j);
				
				doms.put(var, dom);			
			}
		}
		
		// Construct the arrays of variables and domains
		String[] outVars = new String [doms.size()];
		V[][] outDoms = (V[][]) Array.newInstance(this.domains.getClass().getComponentType(), doms.size());
		nbrVars = 0;
		long nbrUtils = 1;
		for (Map.Entry< String, V[] > entry : doms.entrySet()) {
			outVars[nbrVars] = entry.getKey();
			V[] dom = entry.getValue();
			outDoms[nbrVars++] = dom;
			assert Math.log(nbrUtils) + Math.log(dom.length) < Math.log(Long.MAX_VALUE) : 
				"Number of solutions in a space too large for a long";
			nbrUtils *= dom.length;
		}
		
		if (minNCCCs) { // minimize the NCCC count, at the expense of runtime
			
			// Create the output hypercube, with an initially empty array of utilities
			assert Math.log(nbrUtils) < Math.log(Integer.MAX_VALUE) : "Number of solutions in a hypercube too large for an int: " + nbrUtils + " > " + Integer.MAX_VALUE;
			U[] outUtils = (U[]) Array.newInstance(this.values.getClass().getComponentType(), (int)nbrUtils);
			Hypercube<V, U> out = this.newInstance(outVars, outDoms, outUtils, this.infeasibleUtil);

			// Initialize the output utilities with the caller hypercube's utilities
			UtilitySolutionSpace.Iterator<V, U> outIter = out.iterator(this.variables, this.domains);
			UtilitySolutionSpace.Iterator<V, U> myIter = this.iterator(this.variables, outIter.getDomains());
			long utilFactor = outIter.getNbrSolutions() / myIter.getNbrSolutions();
			while (myIter.hasNext()) {
				U util = myIter.nextUtility();
				for (long j = 0; j < utilFactor; j++) {
					outIter.nextSolution();
					outIter.setCurrentUtility(util);
				}
			}

			// Add/multiply to the output utilities the utilities of each input space
			for (UtilitySolutionSpace<V, U> space : spaces) {
				String[] vars = space.getVariables();
				outIter = out.iterator(vars, space.getDomains());
				myIter = space.iterator(vars, outIter.getDomains());
				utilFactor = outIter.getNbrSolutions() / myIter.getNbrSolutions();
				while (myIter.hasNext()) {
					final U util = myIter.nextUtility();
					for (long j = 0; j < utilFactor; j++) 
						if (addition) 
							outIter.setCurrentUtility(util.add(outIter.nextUtility()));
						else 
							outIter.setCurrentUtility(util.multiply(outIter.nextUtility()));
				}
			}
			
			return out;
			
		} else // optimize runtime, not caring about the (irrelevant) NCCC count
			return new JoinOutputHypercube<V, U> (this, spaces, outVars, outDoms, addition, this.infeasibleUtil, nbrUtils);
	}
	
	/** @see UtilitySolutionSpace#multiply(UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<V, U> multiply(UtilitySolutionSpace<V, U> space, String[] total_variables) {
		
		if (! this.knows(space.getClass()) && space.knows(this.getClass())) 
			return space.multiply(this, total_variables);

		// Reduce total_variables to only contain relevant variables
		ArrayList<String> outputVars = new ArrayList<String> (total_variables.length);
		for (String var : total_variables) 
			if (space.getDomain(var) != null || this.getDomain(var) != null) 
				outputVars.add(var);
		
		return this.join(space, outputVars.toArray(new String [outputVars.size()]), false, false);
	}

	/** @see UtilitySolutionSpace#multiply(UtilitySolutionSpace) */
	public UtilitySolutionSpace<V, U> multiply(UtilitySolutionSpace<V, U> space) {

		if (! this.knows(space.getClass()) && space.knows(this.getClass())) 
			return space.multiply(this);

		return this.join(space, false, false);
	}
	
	/** @see UtilitySolutionSpace#multiply(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<V, U> multiply(UtilitySolutionSpace<V, U>[] spaces) {
		return this.join(spaces, false, false);
	}
	
	/** @see BasicHypercube#slice(java.lang.String[], V[][]) */
	@Override
	public Hypercube<V, U> slice( String[] variables_names, V[][] sub_domains ) {
		return (Hypercube<V, U>) super.slice(variables_names, sub_domains);
	}
	
	/** @see BasicHypercube#slice(java.lang.String[], V[]) */
	@Override
	public Hypercube<V, U> slice(String[] variables_names, V[] values) {
		return (Hypercube<V, U>) super.slice(variables_names, values);
	}
	
	/** @see BasicHypercube#slice(String, V[]) */
	@Override
	public Hypercube<V, U> slice(String var, V[] subDomain) {
		return (Hypercube<V, U>) super.slice(var, subDomain);
	}
	
	/** @see BasicHypercube#slice(String, Addable) */
	@Override
	public Hypercube<V, U> slice ( String var, V val ) {
		return (Hypercube<V, U>) super.slice(var, val);
	}
	
	/** @see BasicHypercube#slice(V[]) */
	@Override
	public Hypercube< V, U > slice( V[] variables_values ) {
		return (Hypercube<V, U>) super.slice(variables_values);
	}
	
	/** Joins this hypercube with the provided hypercube
	 * if the utility values array is big enough, this hypercube is modified into the new hypercube,
	 * otherwise a new hypercube is created
	 * @note no assumption on the variables order in the two hypercubes is made, but the variables order in the new hypercube is fixed,
	 *       ie. the first variables will necessarily be the variables present only in the provided variables, then the variables present in this hypercube
	 * @param space           the hypercube to join this one with
	 * @param total_variables the list of variables of the two hypercubes
	 * @return the join of the two hypercubes
	 * @todo Count NCCCS
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace< V, U > applyJoin( UtilitySolutionSpace< V, U > space, String[] total_variables) {
		
		Hypercube<V, U> hypercube = (Hypercube<V, U>) space;
		
		Hypercube<V, U> current_hypercube = this;
		
		if( hypercube.isNull() )
			return NullHypercube.NULL;
		
		if (hypercube.getNumberOfVariables() == 0) { // the input hypercube is a ScalarHypercube
			return hypercube.applyJoin(this, total_variables);
		}
		
		//the number of variables in provided hypercube
		String[] variables2 = hypercube.getVariables();
		int number_of_variables2 = variables2.length;
		
		//the number of variables in resulting hypercube
		int number_of_variables = total_variables.length;
		
		//this array will contain the index of each variable of the provided in the new order
		int [] variables2_indexes = new int[number_of_variables];
		Arrays.fill(variables2_indexes, -1);
		
		String variable, tmp_variable;
		
		//the array that will contain the variables names of the new hypercube
		String[] new_variables = new String[ number_of_variables ];
		//the array that will contain the variables names in an order consistent with the second hypercube
		String[] new_variables2 = new String[ number_of_variables ];
		//the array that will keep the position of each variable of the second hypercube in the variables list of the new hypercube
		int[] position_in_index = new int[ number_of_variables2 ];
		//the array that will contain the domains of the variables of the new hypercube
		V[][] new_domains = (V[][]) new Addable[ number_of_variables ][];
		
		//these arrays will contain the variables which belong to this hypercube
		//and the new domain associated to these variables after this hypercube has been sliced
		ArrayList<String> variables_to_slice_list = new ArrayList<String>();
		ArrayList<V[]> domains_to_slice_list = new ArrayList<V[]>();
		
		//these arrays will contain the variables and domains which belong only to the provided hypercube
		//so that this hypercube can be augmented with these new variables
		ArrayList<String> variables_to_augment_list = new ArrayList<String>();
		ArrayList<V[]> domains_to_augment_list = new ArrayList<V[]>();
		
		V[] domain, domain_tmp;
		int index1 = 0, index2 = 0, index = 0;
		for( int i = 0; index < number_of_variables; i++ ) {
			domain_tmp = null;
			variable = total_variables[ i ];
			tmp_variable = variable;
			
			domain = getDomain( variable, index1 );
			for (int j = 0 ; j < number_of_variables2 ; j++) {
				if (variables2[j].equals(variable)) {
					domain_tmp = hypercube.getDomain(j);
					break;
				}
			}
			
			if( domain == null ){
				if ( domain_tmp != null ) {

					//this variable is present in the provided hypercube but not in this hypercube
					domain = domain_tmp;
					variables_to_augment_list.add(variable);
					domains_to_augment_list.add(domain);
				}
			}
			else{
				//this variable is present in this hypercube
				//if this variable is present in both hypercubes, compute the intersection of its domain in both hypercubes
				if( domain_tmp != null ) {
					domain = intersection( domain, domain_tmp );
					
					//return NULL in case the intersection of two domains of the same variable is empty.
					if( domain == null ) return NullHypercube.NULL;
					
				}

				variables_to_slice_list.add(variable);
				domains_to_slice_list.add(domain);	
				
				index1++;
			}
			
			if( domain != null ) {
				index++;
			}
		}
		
		//fill the variables and domains arrays used to slice and augment this hypercube
		String[] variables_to_slice = (String[]) variables_to_slice_list.toArray(new String[variables_to_slice_list.size()]);
		V[][] domains_to_slice = (V[][]) domains_to_slice_list.toArray(new Addable[domains_to_slice_list.size()][]);
		int number_of_variables_to_slice = variables_to_slice.length;
		
		String[] variables_to_augment = (String[]) variables_to_augment_list.toArray(new String[variables_to_augment_list.size()]);
		V[][] domains_to_augment = (V[][]) domains_to_augment_list.toArray(new Addable[domains_to_augment_list.size()][]);
		int number_of_variables_to_augment = variables_to_augment.length;
		
		System.arraycopy(variables_to_augment, 0, new_variables, 0, number_of_variables_to_augment);
		System.arraycopy(variables_to_slice, 0, new_variables, number_of_variables_to_augment, number_of_variables_to_slice);
		
		System.arraycopy(domains_to_augment, 0, new_domains, 0, number_of_variables_to_augment);
		System.arraycopy(domains_to_slice, 0, new_domains, number_of_variables_to_augment, number_of_variables_to_slice);
		
		//as the variables in the second hypercube can be in any order, we have to keep the index of each of its variables
		//in the variables list of the new hypercube
		for (int i = 0 ; i < number_of_variables ; i++) {
			variable = new_variables[i];
			tmp_variable = variable;
			for (int j = 0 ; j < number_of_variables2 ; j++) {
				if (variables2[j].equals(variable)) {
					variables2_indexes[i] = j;
					tmp_variable = variables2[index2];
					position_in_index[index2] = i;
					index2++;
					break;
				}
			}
			new_variables2[i] = tmp_variable;
		}
		
		current_hypercube = (Hypercube<V, U>) current_hypercube.applySlice(variables_to_slice, domains_to_slice);
		current_hypercube = (Hypercube<V, U>) current_hypercube.applyAugment(variables_to_augment, domains_to_augment);
		
		//this array will contain the values of the variables for the first hypercube
		V[] variables_values = (V[]) new Addable[ number_of_variables ];
		//this array will contain the values of the variables for the second hypercube
		V[] variables_values2 = (V[]) new Addable[ number_of_variables ];
		//this object is used to compute the sum of the utility values
		U utility_value;
		//index of the index of the variable to increment
		int index_to_increment = number_of_variables-1;
		
		int[] indexes = new int[ number_of_variables ];
		
		for( int i = 0; i < current_hypercube.number_of_utility_values; i++ ) {
			
			//the next possible array of values
			for(int j = number_of_variables - 1; j >= 0; j--) {
				index = indexes[ j ];
				domain = new_domains[ j ];
				//next possible value of the jth variable
				variables_values[j] = domain[index];
				if (variables2_indexes[j] == -1)
					variables_values2[j] = domain[index];
				else
					variables_values2[position_in_index[variables2_indexes[j]]] = domain[index];
				
				if( j == index_to_increment ) {
					index = (index + 1) % domain.length;
					indexes[ j ] = index;
					
					// when a whole loop is done increment also the next variable which is previous to this one in order
					if(index == 0)  index_to_increment--;
					else index_to_increment = number_of_variables-1;
				}
			}
			
			//update the utility value by adding the corresponding utility in the second hypercube
			utility_value = hypercube.getUtilityValueSameOrder( new_variables2, variables_values2 );
			current_hypercube.values[i] = (U) utility_value.add(current_hypercube.values[i]);

		}
		return current_hypercube;
	}
	

	/** joins this hypercube with the provided hypercube by using applyJoin method
	 * @param space hypercube to join this one with
	 * @return the join of the two hypercubes
	 * @note Depending on the respective sizes of the caller and the input spaces, 
	 * this method will modify one or the other (it will modify the bigger one). 
	 */
	public UtilitySolutionSpace< V, U > applyJoin( UtilitySolutionSpace< V, U > space) {
		
		Hypercube<V, U> hypercube = (Hypercube<V, U>) space;
		
		if (this.values.length > hypercube.values.length) {
			String[] union = union(this.variables, hypercube.variables);
			return this.applyJoin(hypercube, union);
		}
		else {
			String[] union = union(hypercube.variables, this.variables);
			return hypercube.applyJoin(this, union);
		}
	}
	
	
	/** @see UtilitySolutionSpace#consensus(java.lang.String, java.util.Map, boolean) */
	public ProjOutput< V, U > consensus (final String varOut, final Map< String, UtilitySolutionSpace<V, U> > distributions, final boolean maximum) {
		return this.consensus(varOut, distributions, maximum, false, false);
	}
	
	/** @see UtilitySolutionSpace#consensusAllSols(java.lang.String, java.util.Map, boolean) */
	public ProjOutput<V, U> consensusAllSols (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum) {
		return this.consensus(varOut, distributions, maximum, true, false);
	}
	
	/** @see UtilitySolutionSpace#consensusExpect(java.lang.String, java.util.Map, boolean) */
	public ProjOutput< V, U > consensusExpect (final String varOut, final Map< String, UtilitySolutionSpace<V, U> > distributions, final boolean maximum) {
		return this.consensus(varOut, distributions, maximum, false, true);
	}
	
	/** @see UtilitySolutionSpace#consensusAllSolsExpect(java.lang.String, java.util.Map, boolean) */
	public ProjOutput<V, U> consensusAllSolsExpect (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum) {
		return this.consensus(varOut, distributions, maximum, true, true);
	}
	
	/** A projection operation that uses the consensus approach
	 * @param varOut 			the variable that is projected out
	 * @param distributions 	for each random variable, its weighted samples/probability distribution
	 * @param maximum 			\c true if we should maximize the utility; \c false if it should be minimized
	 * @param allSolutions 		if \c true, use the revised consensus algorithm that considers \b all optimal solutions to each scenario
	 * @param expect 			whether to compose the consenus operation with the expectation operation
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 * @todo Test when the distributions and the caller do not agree on the domains. 
	 */
	@SuppressWarnings("unchecked")
	protected ProjOutput< V, U > consensus (final String varOut, final Map< String, UtilitySolutionSpace<V, U> > distributions, 
			final boolean maximum, final boolean allSolutions, final boolean expect) {
		
		assert ! distributions.containsKey(varOut) : "The provided distributions contain the variable " + varOut + " to be projected out";

		// If this hypercube does not contain the variable to be projected out, 
		// return a clone and NULL optimal conditional assignments 
		if (this.getDomain(varOut) == null) 
			return new ProjOutput<V, U> (expect ? this.expectation(distributions) : this.clone(), new String [0], NullHypercube.NULL);
		
		// Choose the following order to iterate on the variables: 
		// 1) varsKept: the variables that are not projected out and for which no distribution is provided
		// 2) randVars: the random variables in the space for which a distribution is provided 
		// 3) varOut: the variable being projected out
		final int myNbrVars = this.variables.length;
		String[] iterOrder = new String [myNbrVars];
		Class< ? extends V[] > domClass = (Class<? extends V[]>) this.domains.getClass().getComponentType();
		V[][] iterDomsNotOut = (V[][]) Array.newInstance(domClass, myNbrVars);
		int nbrVarsKept = myNbrVars - 1;
		String[] iterOrderNotOut = new String [nbrVarsKept];
		iterOrder[nbrVarsKept] = varOut;
		iterDomsNotOut[nbrVarsKept] = this.getDomain(varOut);
		final int indexVarOut = nbrVarsKept;
		ArrayList<String> varsKept = new ArrayList<String> (nbrVarsKept);
		for (String var : this.variables) {
			UtilitySolutionSpace<V, U> prob = distributions.get(var);
			if (prob != null) { // random variable with a provided distribution
				iterOrder[--nbrVarsKept] = var;
				iterDomsNotOut[nbrVarsKept] = intersection(this.getDomain(var), prob.getDomain(var));
			} else // a varKept or varOut
				varsKept.add(var);
		}
		int nbrRandVars = myNbrVars - nbrVarsKept - 1;
		
		// If none of the input random variables is in this hypercube, return the standard projection
		if (nbrRandVars == 0) 
			return this.project(varOut, maximum);
		
		// Finish filling in the iteration order using varsKept
		varsKept.remove(varOut);
		int nbrVarsKeptUtils = 1;
		for (int i = 0; i < nbrVarsKept; i++) {
			String var = varsKept.get(i);
			iterOrder[i] = var;
			V[] dom = this.getDomain(var);
			iterDomsNotOut[i] = dom;
			nbrVarsKeptUtils *= dom.length;
		}
		System.arraycopy(iterOrder, 0, iterOrderNotOut, 0, indexVarOut);
		final int varOutDomSize = this.getDomain(varOut).length;
		
		// Construct the iterators
		final UtilitySolutionSpace.Iterator<V, U> myIter = this.iterator(iterOrder, iterDomsNotOut);
		final UtilitySolutionSpace.Iterator<V, AddableReal>[] iters = new UtilitySolutionSpace.Iterator [nbrRandVars];
		int nbrRandUtilsTmp = 1; // number of combinations of assignments to all randVars
		for (int i = myNbrVars - 2; i >= nbrVarsKept; i--) {
			Hypercube<V, AddableReal> probLaw = (Hypercube<V, AddableReal>) distributions.get(iterOrderNotOut[i]);
			assert probLaw.getNumberOfVariables() == 1 : "Does not currently support probability laws over more than one variable: " + probLaw;
			iters[i - nbrVarsKept] = probLaw.iterator(iterOrderNotOut, iterDomsNotOut);
			nbrRandUtilsTmp *= probLaw.domains[0].length;
		}
		final int nbrRandUtils = nbrRandUtilsTmp;
		
		// The utility arrays of the optimal conditional assignments and the optimal expected utilities
		ArrayList<V>[] optSols = new ArrayList [ nbrVarsKeptUtils ];
		U[] expectUtils = expect ? (U[]) Array.newInstance(this.getClassOfU(), nbrVarsKeptUtils) : null;
		
		// For a given assignment to varOut, the probability that it be optimal
		int i;
		HashMap<V, Double> counts;
		Double maxProb;
		V optSol;
		Double maxProb2; // second highest prob
		double probLeft;
		
		int j;
		U optUtil;
		final U infeasibleUtil = (maximum ? this.infeasibleUtil.getMinInfinity() : this.infeasibleUtil.getPlusInfinity());
		ArrayList<V> optVals;
		HashMap<V, U> partialExpect = expect ? new HashMap<V, U> (varOutDomSize) : null;
		HashMap<V, U> zeroExpect = expect ? new HashMap<V, U> (varOutDomSize) : null;
		if (expect) 
			for (V val : this.getDomain(varOut)) 
				zeroExpect.put(val, this.infeasibleUtil.getZero());
		U expectUtil = null;
		
		int k;
		U util;
		int diffUtil;
		
		double prob;
		U probAddable = null;
		
		Double count;
		
		V sol;
		
		ArrayList<V> tmp;
		
		// Go through all possible assignments to the varsKept
		for (i = 0; myIter.hasNext(); i++) {
			
			// For a given assignment to varOut, the probability that it be optimal
			counts = new HashMap<V, Double> ();
			maxProb = 0.0;
			optSol = null;
			maxProb2 = 0.0; // second highest prob
			probLeft = 1.0;
			
			// Go through all possible assignments to the randVars
			j = -1;
			if (expect) 
				partialExpect.putAll(zeroExpect);
			scenariosLoop: while (++j < nbrRandUtils) {
				
				// Initialize the best solution
				optUtil = infeasibleUtil;
				optVals = new ArrayList<V> ();
					
				// Compute the probability of the current assignment to the randVars
				prob = 1.0;
				for (UtilitySolutionSpace.Iterator<V, AddableReal> iter : iters) 
					prob *= iter.nextUtility().doubleValue();
				if (expect) probAddable = (U) new AddableReal (prob);
				probLeft -= prob;
				
				// Go through all possible assignments to the varOut to find the optimal one(s)
				for (k = 0; k < varOutDomSize; k++) {
					sol = myIter.nextSolution()[indexVarOut];
					
					if (expect) // start computing the expectation
						partialExpect.put(sol, partialExpect.get(sol).add((util = myIter.getCurrentUtility()).multiply(probAddable)));
					else 
						util = myIter.getCurrentUtility(optUtil, !maximum);
					
					diffUtil = util.compareTo(optUtil); /// @bug Can be 0 even if the true utility of the current solution is worse
					if ((maximum && diffUtil >= 0) || (! maximum && diffUtil <= 0)) { // best assignment to varOut found so far
						
						// Check if this assignment is strictly better than the current optimal one
						if (diffUtil != 0) {
							optUtil = util;
							optVals.clear();
						}
						
						// Record the current assignment
						optVals.add(sol);
					}
				}
				
				// Record the best solution(s) found
				if (allSolutions) { // we want to consider all solutions
					
					// Record all best solutions found except the first (which will be recorded later)
					for (k = optVals.size() - 1; k >= 1; k--) {
						sol = optVals.get(k);
						
						// Look up the current probability of optimality of this solution
						count = counts.get(sol);
						if (count == null) { // current probability is 0
							count = prob;
						} else 
							count += prob;
						counts.put(sol, count);
						
						// Check if this solution has the highest probability so far
						if (count.compareTo(maxProb) >= 0) {
							
							// Replace the second best with the current first best if the first best has changed
							if (! sol.equals(optSol)) 
								maxProb2 = maxProb;
							
							// Replace the first best with the new one found
							maxProb = count;
							optSol = sol;
							
						} else if (count.compareTo(maxProb2) > 0) // new second best
							maxProb2 = count;
					}
				}
				
				// Record the first best solution found
				sol = optVals.get(0);
				
				// Look up the current probability of optimality of this solution
				count = counts.get(sol);
				if (count == null) { // current probability is 0
					count = prob;
				} else 
					count += prob;
				counts.put(sol, count);
				
				// Check if this solution has the highest probability so far
				if (count.compareTo(maxProb) >= 0) {
					
					// Replace the second best with the current first best if the first best has changed
					if (! sol.equals(optSol)) 
						maxProb2 = maxProb;
					
					// Replace the first best with the new one found
					maxProb = count;
					optSol = sol;
					
				} else if (count.compareTo(maxProb2) > 0) // new second best
					maxProb2 = count;
				
				// If there isn't enough probability left for the second best to catch up with the first, then skip the other scenarios
				if (maxProb - maxProb2 >= probLeft) 
					break scenariosLoop;
			}
			
			if (expect) expectUtil = partialExpect.get(optSol);
			
			// Finish iterating without computing utilities if the iteration was interrupted
			while (++j < nbrRandUtils) {
				
				if (expect) { // we actually need to compute the probability and utility to compute the expectation 
					prob = 1.0;
					for (UtilitySolutionSpace.Iterator<V, AddableReal> iter : iters) 
						prob *= iter.nextUtility().doubleValue();
					probAddable = (U) new AddableReal (prob);
					probLeft -= prob;
				} else 
					for (UtilitySolutionSpace.Iterator<V, AddableReal> iter : iters) 
						iter.nextSolution();

				for (k = 0; k < varOutDomSize; k++) {
					sol = myIter.nextSolution()[indexVarOut];
					if (expect && optSol.equals(sol)) 
						expectUtil = expectUtil.add(myIter.getCurrentUtility().multiply(probAddable));
				}

			}
			
			// Record the best solution found
			tmp = new ArrayList<V> (1);
			tmp.add(optSol);
			optSols[i] = tmp;
			if (expect) expectUtils[i] = expectUtil;
		}
		
		// Generate the BasicHypercube of optimal conditional assignments and the composition
		BasicHypercube< V, ArrayList<V> > optAssignments;
		UtilitySolutionSpace< V, U > composition;
		String[] varsOut = new String[] { varOut };
		if (nbrVarsKept > 0) {
			V[][] varsKeptDoms = (V[][]) Array.newInstance(domClass, nbrVarsKept);
			System.arraycopy(iterDomsNotOut, 0, varsKeptDoms, 0, nbrVarsKept);
			String[] varsKeptArray = varsKept.toArray(new String [nbrVarsKept]);
			optAssignments = new BasicHypercube< V, ArrayList<V> > (varsKeptArray, varsKeptDoms, optSols, null);
			
			if (expect) 
				composition = new Hypercube< V, U > (varsKeptArray, varsKeptDoms, expectUtils, this.infeasibleUtil);
			else 
				composition = this.compose(varsOut, optAssignments);
			
		} else { // nbrVarsKept == 0
			optAssignments = new ScalarBasicHypercube< V, ArrayList<V> > (optSols[0], null);
			if (expect) 
				composition = new ScalarHypercube< V, U > (expectUtils[0], this.infeasibleUtil, domClass);
			else 
				composition = this.compose(varsOut, optAssignments);
		}
		
		return new ProjOutput<V, U> (composition, varsOut, optAssignments);
	}

	/**Returns a Hypercube obtained by projecting some of the variables of the hypercube
	 * @param varsOut 	the variables that should be removed from this hypercube
	 * @param maximum 	boolean indicating whether to use the maximum or the minumum
	 * @return a ProjOutput object
	 * @author Thomas Leaute
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace.ProjOutput< V, U > project( String[] varsOut, final boolean maximum ) {
		
		assert contains(varsOut) : "A hypercube must contain all the variables that must be projected out";
		
		// Number of variables in this hypercube
		final int myNbrVars = variables.length;
		
		// Number of variables to project out
		final int nbrVarsOut = varsOut.length;

		if( nbrVarsOut == 0 )
			return new ProjOutput<V, U> (this.clone(), new String [0], NullHypercube.NULL);
		
		//if all variables must be projected out
		if( nbrVarsOut == myNbrVars ) 
			return this.projectAll(maximum, varsOut);
		
		// Number of variables in the output
		final int nbrVarsKept = myNbrVars - nbrVarsOut;

		// Generate a variable order for the iteration that puts last the variables to be projected out
		HashSet<String> varsOutSet = new HashSet<String> (Arrays.asList(varsOut));
		String[] varOrder = new String [myNbrVars];
		Class<?> domClass = this.domains.getClass().getComponentType();
		V[][] domsKept = (V[][]) Array.newInstance(domClass, nbrVarsKept);
		int nbrUtilsKept = 1;
		int i = 0;
		for (int j = 0 ; j < myNbrVars; j++) {
			String var = this.variables[j];
			
			if (! varsOutSet.contains(var)) {
				V[] dom = this.domains[j];
				domsKept[i] = dom;
				varOrder[i++] = var;
				assert Math.log((double) nbrUtilsKept) + Math.log((double) dom.length) < Math.log(Integer.MAX_VALUE) : 
					"Size of utility array too big for an int";
				nbrUtilsKept *= dom.length;
			}
		}
		System.arraycopy(varsOut, 0, varOrder, i, nbrVarsOut);
		
		// The number of possible assignments to the variables being projected out
		final long nbrUtilsOut = this.getNumberOfSolutions() / nbrUtilsKept;
		
		// If all projected variables have singleton domains, the conditional optimal assignments can be made scalar. 
		if (nbrUtilsOut == 1) {
			
			// Build the (constant) optimal solution
			V[] domsOut = (V[]) Array.newInstance(domClass.getComponentType(), nbrVarsOut);
			ArrayList<V> optSol = new ArrayList<V> (nbrVarsOut);
			for (i = 0; i < nbrVarsOut; i++) 
				optSol.add(domsOut[i] = this.getDomain(varsOut[i])[0]);
			
			return new ProjOutput<V, U> (
					this.slice(varsOut, domsOut), 
					varsOut, 
					new ScalarBasicHypercube< V, ArrayList<V> > (optSol, null));
		}
		
		// Build the output array of kept variables
		String[] varsKept = new String [nbrVarsKept];
		System.arraycopy(varOrder, 0, varsKept, 0, nbrVarsKept);

		// Initialize the output arrays of utilities
		U[] optUtils = (U[]) Array.newInstance(this.getClassOfU(), nbrUtilsKept);
		ArrayList<V>[] optSols = new ArrayList [nbrUtilsKept];
		
		// Iterate over the solutions in the space
		UtilitySolutionSpace.Iterator<V, U> iter = this.iterator(varOrder);
		V[] optSol = (V[]) Array.newInstance(this.classOfV, nbrVarsOut);
		U optUtil;
		for (i = 0; iter.hasNext(); i++) {
			
			// Look up the best assignment to the variables projected out for the current assignment to the variables kept
			if (maximum) {
				optUtil = this.infeasibleUtil.getMinInfinity();
			} else 
				optUtil = this.infeasibleUtil.getPlusInfinity();
			
			// Iterate over all possible assignments to the variables projected out
			for (long j = 0; j < nbrUtilsOut; j++) {
				iter.nextSolution();
				U util = iter.getCurrentUtility(optUtil, !maximum);
				
				if ((maximum && util.compareTo(optUtil) >= 0) || (!maximum && util.compareTo(optUtil) <= 0)) {
					optUtil = util;
					System.arraycopy(iter.getCurrentSolution(), nbrVarsKept, optSol, 0, nbrVarsOut);
				}
			}
			
			optUtils[i] = optUtil;
			optSols[i] = new ArrayList<V> (Arrays.asList(optSol));
		}
		
		return new ProjOutput<V, U> (new Hypercube< V, U>( varsKept, domsKept, optUtils, this.infeasibleUtil), 
				varsOut, 
				 new BasicHypercube< V, ArrayList<V> > (varsKept.clone(), domsKept.clone(), optSols, null));
	}
	
	/** @see HypercubeLimited#blindProject(java.lang.String, boolean) */
	@Override
	public UtilitySolutionSpace<V, U> blindProject (String varOut, boolean maximize) {
		return this.blindProject(new String[] {varOut}, maximize);
	}
	
	/** @see HypercubeLimited#blindProject(java.lang.String[], boolean) */
	@SuppressWarnings("unchecked")
	@Override
	public UtilitySolutionSpace<V, U> blindProject (String[] varsOut, boolean maximize) {

		// Only project variables that are actually contained in this space
		HashSet<String> varsOutSet = new HashSet<String> (varsOut.length);
		for (String varOut : varsOut) 
			if (this.getDomain(varOut) != null) 
				varsOutSet.add(varOut);
		int nbrVarsOut = varsOutSet.size();
		if( nbrVarsOut == 0 )
			return this;
		if (nbrVarsOut == this.getNumberOfVariables()) 
			return new ScalarHypercube<V, U> (this.blindProjectAll(maximize), this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass());
		if (nbrVarsOut < varsOut.length) 
			varsOut = varsOutSet.toArray(new String [nbrVarsOut]);
		
		return new BlindProjectOutput<V, U> (this, varsOut, maximize, this.infeasibleUtil);
	}

	/** @see HypercubeLimited#blindProjectAll(boolean) */
	@Override
	public U blindProjectAll(final boolean maximize) {
		
		final U inf = maximize ? this.infeasibleUtil.getMinInfinity() : this.infeasibleUtil.getPlusInfinity();
		
		// Compute the optimum utility value
		BasicUtilitySolutionSpace.SparseIterator<V, U> iter = this.sparseIter(inf);
		U optimum = iter.nextUtility();
		if (optimum == null) 
			return inf;
		
		U util = null;
		while ( (util = iter.nextUtility()) != null) {
			if (maximize) 
				optimum = optimum.max(util);
			else 
				optimum = optimum.min(util);
		}
		
		return optimum;
	}

	/** @see HypercubeLimited#min(java.lang.String) */
	@Override
	public Hypercube<V, U> min (String var) {
		return (Hypercube<V, U>) super.min(var);
	}
	
	/** @see HypercubeLimited#max(java.lang.String) */
	@Override
	public Hypercube<V, U> max (String var) {
		return (Hypercube<V, U>) super.max(var);
	}
	
	/**Returns a Hypercube obtained by projecting the input variable out of the hypercube
	 * @param variable_name      the variable that should have been removed in the resulting hypercube
	 * @param maximum            indicates whether one should take the maximum (\c true) or the minimum (\c false)
	 * @return                   a ProjOutput object
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace.ProjOutput< V, U > project( String variable_name, boolean maximum ) {
		
		/// @todo Implement an on-the-fly version of argProject(). 
		
		// If the input variable is not in this hypercube, return a clone and NULL conditional optimal assignments
		if (this.getDomain(variable_name) == null) 
			return new ProjOutput<V, U> (this.clone(), new String [0], NullHypercube.NULL);
		
		return project( new String[] { variable_name }, maximum );
	}
	
	/**Returns a hypercube obtained by projecting out from this hypercube the last \c number_to_project variables of the hypercube
	 * @param number_to_project  number of the varibles of the hypercube to project out
	 * @param maximum            indicates wether to take the maximum or minimum among the utility values that will be mapped into the
	 *                           the same utility value in the new hypercube
	 * @return  a ProjOutput object
	 * @todo Implement an on-the-fly version of this method. 
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace.ProjOutput< V, U > project( int number_to_project, boolean maximum) {
		//number of variables to keep (i.e number of variables of the new hypercube)
		
		int nbrVarsKept = variables.length - number_to_project;
		
		//if all variables must be projected out
		if( nbrVarsKept <= 0 ) 
			return this.projectAll(maximum);
		
		if( number_to_project == 0)
			return new ProjOutput<V, U> (this.clone(), new String [0], NullHypercube.NULL);
		
		int nbrUtilsInit = number_of_utility_values;
		
		//variables of the new hypercube
		String[] new_variables = new String[nbrVarsKept];
		System.arraycopy(variables, 0, new_variables, 0, nbrVarsKept);
		
		//domains of the variables of the new hypercube
		V[][] new_domains = (V[][]) Array.newInstance(this.domains.getClass().getComponentType(), nbrVarsKept);
		System.arraycopy(domains, 0, new_domains, 0, nbrVarsKept);
		
		//compute the number of utility values
		int nbrUtilsKept = 1;
		for( V[] domain : new_domains)
			nbrUtilsKept *= domain.length;
		
		//the array that will contain the utility values of the resulting hypercube
		U[] new_values = (U[]) Array.newInstance(this.values.getClass().getComponentType(), nbrUtilsKept);
		
		// The conditional optimal assignments
		ArrayList<V>[] condOptAssignments = (ArrayList<V>[]) new ArrayList[ nbrUtilsKept ];
		
		// One conditional optimal assignment
		ArrayList<V> condOptAssignment;
		
		int step = steps_hashmaps[ nbrVarsKept - 1 ].get( domains[ nbrVarsKept - 1 ][ 1 ] );
		
		U new_utility;
		int index = 0;
		for( int i = step; i <= nbrUtilsInit; i += step ) {
			new_utility = null;
			condOptAssignment = new ArrayList<V> (number_to_project);
			for (int j = 0; j < number_to_project; j++) {
				condOptAssignment.add(null);
			}
			
			// Index of the optimal utility in the utility array for the current assignments to all kept variables
			int jOpt = i - step;
			
			for( int j = i - step; j < i; j++ ) {
				U util_j = values[ j ];
				this.incrNCCCs(1);
				if( new_utility == null ) {
					new_utility = util_j;
					jOpt = j;
				} else if( (maximum && util_j.compareTo( new_utility ) > 0) ||
						   (! maximum && util_j.compareTo( new_utility ) < 0)) {
					new_utility = util_j;
					jOpt = j;
				}
			}

			// Compute the optimal assignments
			int modulo = 1;
			for ( int j = variables.length - 1; j >= nbrVarsKept; j-- ) { // for each projected variable
				V[] domain = domains[j];
				condOptAssignment.set(j - nbrVarsKept, domain[(jOpt % (modulo * domain.length)) / modulo]);
				modulo *= domain.length;
			}
			condOptAssignments[index] = condOptAssignment;

			new_values[ index ] = new_utility;
			index++;
		}
		
		String[] varsOut = new String [number_to_project];
		System.arraycopy(this.variables, nbrVarsKept, varsOut, 0, number_to_project);
				
		return new ProjOutput<V, U> (new Hypercube< V, U >( new_variables, new_domains, new_values, this.infeasibleUtil ), 
				varsOut, 
				new BasicHypercube< V, ArrayList<V> > (new_variables, new_domains, condOptAssignments, null));
	}
	
	/** @see UtilitySolutionSpace#projectAll(boolean) */
	public ProjOutput<V, U> projectAll(boolean maximum) {
		return this.projectAll(maximum, variables);
	}
	
	/** @see UtilitySolutionSpace#projectAll(boolean, String[]) */
	@SuppressWarnings("unchecked")
	public ProjOutput<V, U> projectAll(boolean maximum, String[] varsOut) {
		
		// Compute the optimum utility value
		U optimum = values[0];
		int optIndex = 0;
		for (int i = 0; i < number_of_utility_values; i++) {
			U val = values[i];
			if ((maximum && val.compareTo(optimum) >= 0) || (!maximum && val.compareTo(optimum) <= 0)) {
				optimum = val;
				optIndex = i;
			}
		}
		this.incrNCCCs(this.number_of_utility_values);
		
		// Find the corresponding optimal assignments
		int step = 0, tmp;
		HashMap<String, V> opt = new HashMap<String, V> ();
		for (int i = 0; i < variables.length; i++) {
			V[] dom = domains[i];
			HashMap<V, Integer> steps = steps_hashmaps[i];
			V optVal = dom[0];
			for (V val : dom) {
				tmp = steps.get(val);
				if (tmp <= optIndex) {
					step = tmp;
					optVal = val;
				} else {
					break;
				}
			}
			opt.put(this.variables[i], optVal);
			optIndex -= step;
		}
		
		// Build the array of optimal values, following the order in varsOut
		ArrayList<V> optValues = new ArrayList<V> (variables.length);
		for (int i = 0; i < variables.length; i++) 
			optValues.add(opt.get(varsOut[i]));

		return new ProjOutput<V, U> (new ScalarHypercube<V, U>(optimum, this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass()), varsOut, new ScalarBasicHypercube< V, ArrayList<V> > (optValues, null));
	}

	/**Returns a Hypercube obtained by projecting some of the variables of the hypercube
	 * this version of project does not create a new hypercube but directly modifies the current utility array
	 * @param variables_names the variables that should be removed from this hypercube
	 * @param maximum         boolean object indicating whether to use the maximum or the minumum
	 * @return a ProjOutput object with the modifies hypercube
	 * @todo Count NCCCS	 
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace.ProjOutput< V, U > applyProject( String[] variables_names, final boolean maximum ) {
		
		assert contains(variables_names) : "A hypercube must contain all the variables provided in the variables_names parameter";
		
		//number of variables in this hypercube
		int myNbrVars = variables.length;
		
		//number of variables to project out
		int nbrVarsOut = variables_names.length;
		
		//if there are no variables to project out
		if( nbrVarsOut == 0 )
			return new ProjOutput<V, U> (this.clone(), new String [0], NullHypercube.NULL);
		
		//if all variables must be projected out
		if( nbrVarsOut == myNbrVars ) {
			
			// Compute the optimum utility value
			U optimum = values[0];
			int optIndex = 0;
			for (int i = 0; i < number_of_utility_values; i++) {
				U val = values[i];
				if ((maximum && val.compareTo(optimum) > 0) || (!maximum && val.compareTo(optimum) < 0)) {
					optimum = val;
					optIndex = i;
				}
			}
			
			// Find the corresponding optimal assignments
			ArrayList<V> optValues = new ArrayList<V> (variables.length);
			int step = 0, tmp;
			for (int i = 0; i < variables.length; i++) {
				V[] dom = domains[i];
				HashMap<V, Integer> steps = steps_hashmaps[i];
				V optVal = dom[0];
				for (V val : dom) {
					tmp = steps.get(val);
					if (tmp <= optIndex) {
						step = tmp;
						optVal = val;
					} else {
						break;
					}
				}
				optValues.add(optVal);
				optIndex -= step;
			}
			
			return new ProjOutput<V, U> (new ScalarHypercube<V, U>(optimum, this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass()), 
					this.variables, new ScalarBasicHypercube< V, ArrayList<V> > (optValues, null));
		}
		
		//number of variables to keep
		int nbrVarsKept = myNbrVars - nbrVarsOut;
		
		//indexes of the variables to project out
		int[] varOutIndexes = new int[nbrVarsOut];
		//indexes of the variables to keep
		int[] varKeptIndexes = new int[nbrVarsKept];
		
		String[] new_variables = new String[ nbrVarsKept ];
		Class<?> domClass = this.domains.getClass().getComponentType();
		V[][] new_domains = (V[][]) Array.newInstance(domClass, nbrVarsKept);
		
		// The number of utilities in the resulting hypercube
		int nbrUtilsKept = 1;
		
		//compute the size of the new utility values array and fill the array containing the variables and the one containing the 
		//their domains in the new hypercube
		boolean not_found; 
		int index = 0;
		String variable;
		V[] domain;
		
		for( int i = 0; i < myNbrVars; i++ ) {
			variable = variables[ i ];
			domain = domains[ i ];
			not_found = true;
			
			for( int j = 0; j < nbrVarsOut; j++)
				if( variable.equals( variables_names[ j ] ) ) {
					varOutIndexes[ j ] = i;
					
					not_found = false;
				}
			
			if( not_found ) {
				varKeptIndexes[ index ] = i;
				
				new_variables[ index ] = variable;
				new_domains[ index ] = domain;
				index++;
				
				nbrUtilsKept *= domain.length;
			}
		}
		
		//set steps_hashmaps according to the new domains
		this.setStepsHashmaps(new_variables, new_domains, nbrUtilsKept);
		
		this.variables = new_variables;
		
		// The conditional optimal assignments
		ArrayList<V>[] condOptAssignments = (ArrayList<V>[]) new ArrayList[ nbrUtilsKept ];
		
		// One conditional optimal assignment
		ArrayList<V> condOptAssignment;
		
		// For each variable in the input hypercube, the index of its current value
		int[] valIndexes = new int[myNbrVars];
		
		int index_to_increment = myNbrVars - 1;
		
		V[] variables_values = (V[]) Array.newInstance(domClass.getComponentType(), myNbrVars);
		
		V[] kept_variables_values = (V[]) Array.newInstance(domClass.getComponentType(), nbrVarsKept);
		
		U new_utility, new_utility_tmp;
		
		//for all possible values of the variables to keep in the new hypercube, compute the maximum or minimue over all utility values
		//obtained by varying the variables that will be projected out of the hypercube.
		
		for( int i = 0 ; i < number_of_utility_values ; i++) {
			
			condOptAssignment = new ArrayList<V> (nbrVarsOut);
			for (int j = 0; j < nbrVarsOut; j++) {
				condOptAssignment.add(null);
			}
			
			// Find the next possible values for the variables in the original hypercube, 
			for( int j = myNbrVars - 1; j >= 0; j-- ) {
				index = valIndexes[j];
				domain = domains[j];
				
				variables_values[j] = domain[index];
				
				// Increment or not the value index of this variable
				if( j == index_to_increment ) {
					index = ( index + 1 ) % domain.length;
					valIndexes[ j ] = index;
					
					//when a whole loop over all values of this variable is done increment also the next variable which 
					//is previous to this one in order
					if( index == 0 )  index_to_increment--;
					
					else index_to_increment = myNbrVars - 1;
				}
			}
			
			for (int j = 0 ; j < nbrVarsKept ; j++)
				kept_variables_values[j] = variables_values[varKeptIndexes[j]];
			
			new_utility = values[i];
			values[i] = null;
			
			int indexOfUtility = this.getIndexOfUtilityValue(kept_variables_values);
			new_utility_tmp = values[indexOfUtility];
			
			if ( new_utility_tmp == null ||
			     (maximum && ( new_utility.compareTo(new_utility_tmp) > 0 )) ||
			     (!maximum && ( new_utility.compareTo(new_utility_tmp) < 0 )) ) {
				values[indexOfUtility] = new_utility;
				
				for( int j = 0; j < nbrVarsOut; j++ )
					condOptAssignment.set(j, variables_values[ varOutIndexes[j] ]);
				condOptAssignments[indexOfUtility] = condOptAssignment;
				
			}
		}
		
		this.domains = new_domains;
		this.setNumberOfSolutions(nbrUtilsKept);
		
		return new ProjOutput<V, U> (this, variables_names, new BasicHypercube< V, ArrayList<V> > (new_variables, new_domains, condOptAssignments, null));
	}
	
	/**Returns a Hypercube containing variables values corresponding to utility values bigger than the provided threshold
	 * @param threshold the threshold
	 * @param maximum   boolean
	 * @return Hypercube object obtained by removing utility values less than the threshold from the this hypercube
	 */
	@SuppressWarnings("unchecked")
	public Hypercube< V, U > split( U threshold, boolean maximum ) {
		//number of variables in this hypercube
		
		int number_of_variables = variables.length;
		//number of utility variables in this hypercube
		int number_of_utilities = number_of_utility_values;
		
		//the array that will contain the domains of the variables of the resulting hypercube
		Class<?> domClass = this.domains.getClass().getComponentType();
		V[][] new_domains = (V[][]) Array.newInstance(domClass, number_of_variables);
		for(int i = 0; i < number_of_variables; i++)
			new_domains[ i ] = (V[]) Array.newInstance(this.classOfV, domains[i].length);
		
		//an array of indexes used the go through the array of the domains of the variables of the hypercube
		int[] indexes = new int[ number_of_variables ];
		//this array will contain the sizes of the new domains of the variables
		int[] new_domains_sizes = new int[ number_of_variables ];
		//index of the variable which index will be incresed
		int index_to_increment = number_of_variables - 1;
		int domain_size;
		
		//loop over all possible combinations of possible values for the variables of the hypercube
		int index;
		V[] domain;
		for( int i = 0; i < number_of_utilities; i++ ) {
			for( int j = number_of_variables - 1; j >= 0; j-- ) {
				index = indexes[ j ];
				domain = domains[ j ];
				//the current value of the jth variable
				this.assignment[ j ] = domain[ index ];
			}
			//if the utility value corresponding to the built vector of variable values is bigger than the threshold add 
			//the variable values to the new domains array
			this.incrNCCCs(1);
			if( ( maximum && (threshold.compareTo( values[ i ] ) < 0) ) || ( !maximum && (threshold.compareTo( values[ i ] ) > 0) ) ) {
				for( int j = 0; j < number_of_variables; j++ ) {
					index = indexes[ j ];
					if( new_domains[ j ][ index ] == null ) {
						new_domains[ j ][ index ] = this.assignment[ j ];
						new_domains_sizes[ j ]++;
					}
				}
			}
			
			for( int j = number_of_variables - 1; j >= 0; j-- ) {
				//increment or not the value index of this variable
				if( j == index_to_increment ) {
					index = indexes[ j ];
					domain = domains[ j ];
					index = ( index + 1 ) % domain.length;
					indexes[ j ] = index;
					// when a whole loop over all values of this variable is done increment also the next variable which 
					//is previous to this one in order
					if(index == 0)  index_to_increment--;
					else index_to_increment = number_of_variables - 1;
				}
			}
		}
		
		//remove the "null" entries in the array containing the domain of the resulting hypercube and compute the number of utility values
		//of the new Hypercube
		boolean empty_result = true;
		number_of_utilities = 1;
		for(int i = 0; i < number_of_variables; i++ ) {
			domain_size = new_domains_sizes[ i ];
			domain = new_domains[ i ];
			new_domains[ i ] = (V[]) Array.newInstance(this.classOfV, domain_size);
			index = 0;
			for( V v : domain )
				if( v != null ) {
					empty_result = false;
					
					new_domains[ i ][ index ] = v;
					index++;
				}
			number_of_utilities *= domain_size;
		}
		
		//if this hypercube doesn't contain any utility value bigger than the provided threshold
		if( empty_result )
			return NullHypercube.NULL;
		
		/*
		 * now we need to compute the corresponding utility values for the new domain. since not all of them are
		 * above the threshold
		 */
		
		//Construct the array containing the utility values corresponding to the new domains of the variables
		U[] new_values = (U[]) Array.newInstance(this.values.getClass().getComponentType(), number_of_utilities);
		//reset the array of indexes to 0 and the index to increment to the index of the last variable
		java.util.Arrays.fill(indexes, 0, number_of_variables - 1, 0);
		index_to_increment = number_of_variables - 1;
		
		//loop over all the possible values of the variables of the new hypercube and compute the corresponding utility value in this
		//Hypercube
		for( int i = 0; i < number_of_utilities; i++ ) {
			for(int j = number_of_variables - 1; j >= 0; j--){
				index = indexes[ j ];
				domain = new_domains[ j ];
				//the current value of the jth variable
				this.assignment[ j ] = domain[ index ];
				
				//increment or not the value index of this variable
				if(j == index_to_increment){
					index = ( index + 1 ) % domain.length;
					indexes[ j ] = index;
					// when a whole loop over all values of this variable is done, increment also the next variable which 
					//is previous to this one in order
					if(index == 0)  index_to_increment--;
					else index_to_increment = number_of_variables - 1;
				}
			}
			//get the corresponding utility value and insert it to the new hypercube
			new_values[ i ] = getUtility( this.assignment );
		}
		return new Hypercube< V, U >( variables, new_domains, new_values, this.infeasibleUtil );
	}
	
	/** @see UtilitySolutionSpace#expectation(java.util.Map) */
	public UtilitySolutionSpace<V, U> expectation(Map< String, UtilitySolutionSpace<V, U> > distributions) {
		
		if (distributions.isEmpty())
			return this.clone();
		
		// Ignore all random variables not present in the this space
		Map< String, UtilitySolutionSpace<V, U> > myDist = new HashMap< String, UtilitySolutionSpace<V, U> > ();
		for (Map.Entry< String, UtilitySolutionSpace<V, U> > entry : distributions.entrySet()) 
			if (this.getDomain(entry.getKey()) != null) 
				myDist.put(entry.getKey(), entry.getValue());
		
		if (myDist.isEmpty())
			return this.clone();
		
		return new ExpectationOutput<V, U> (this, myDist, this.infeasibleUtil);
	}
	
	/** @see UtilitySolutionSpace#projExpectMonotone(java.lang.String, java.util.Map, boolean) */
	@SuppressWarnings("unchecked")
	public ProjOutput<V, U> projExpectMonotone(final String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, final boolean maximum) {
		
		/// @todo Implement an on-the-fly version of argProjExpectMonotone() like for the join. 
		
		assert ! distributions.containsKey(varOut) : "The provided distributions contain the variable " + varOut + " to be projected out";

		// If this hypercube does not contain the variable to be projected out: 
		if (this.getDomain(varOut) == null) 
			return new ProjOutput<V, U> (this.expectation(distributions), new String [0], NullHypercube.NULL);
		
		if (distributions.isEmpty())
			return this.project(varOut, maximum);
		
		// Choose the following order to iterate on the variables: 
		// 1) varsKept: the variables that are not projected out and for which no distribution is provided
		// 2) varOut: the variable being projected out
		// 3) randVars: the random variables in the space for which a distribution is provided 
		final int myNbrVars = this.variables.length;
		String[] iterOrder = new String [myNbrVars];
		Class<?> domClass = this.domains.getClass().getComponentType();
		V[][] iterOrderDoms = (V[][]) Array.newInstance(domClass, myNbrVars);
		int firstRandVarIndex = myNbrVars;
		int nbrVarsKept = 0;
		int nbrVarsKeptUtils = 1; // number of combinations of assignments to all vars kept
		int nbrRandUtilsTmp = 1; // number of combinations of assignments to all randVars
		UtilitySolutionSpace<V, U> probSpace = null;
		for (String var : this.variables) {
			UtilitySolutionSpace<V, U> dist = distributions.get(var);
			
			if (dist != null) { // random variable with a provided distribution
				iterOrder[--firstRandVarIndex] = var;
				V[] dom = intersection(this.getDomain(var), dist.getDomain(0));
				iterOrderDoms[firstRandVarIndex] = dom;
				nbrRandUtilsTmp *= dom.length;
				
				if (probSpace == null) 
					probSpace = dist;
				else 
					probSpace = probSpace.multiply(dist);
				
			} else if (! var.equals(varOut)) { // a varKept
				iterOrder[nbrVarsKept] = var;
				V[] dom = this.getDomain(var);
				iterOrderDoms[nbrVarsKept++] = dom;
				nbrVarsKeptUtils *= dom.length;
			}
		}
		final int nbrRandUtils = nbrRandUtilsTmp;
		
		// If none of the input random variables is in this hypercube, return the standard projection
		if (firstRandVarIndex == myNbrVars) 
			return this.project(varOut, maximum);
		
		final int indexVarOut = nbrVarsKept;
		iterOrder[indexVarOut] = varOut;
		V[] dom = this.getDomain(varOut);
		iterOrderDoms[indexVarOut] = dom;
		final int varOutDomSize = dom.length;
		
		// Construct the iterators
		final UtilitySolutionSpace.Iterator<V, U> myIter = this.iterator(iterOrder, iterOrderDoms);
		final UtilitySolutionSpace.Iterator<V, U> probIter = probSpace.iterator(iterOrder, iterOrderDoms);
		
		// The utility array of the optimal conditional assignments
		ArrayList<V>[] optSols = new ArrayList [ nbrVarsKeptUtils ];
		ArrayList<V> tmp;
		U[] optUtils = (U[]) Array.newInstance(getClassOfU(), nbrVarsKeptUtils);
		
		// Go through all possible assignments to the varsKept
		int i;
		V optSol;
		final V firstVal = dom[0];
		U optExpUtil;
		final U infeasibleUtil = (maximum ? this.infeasibleUtil.getMinInfinity() : this.infeasibleUtil.getPlusInfinity());
		
		int j;
		U expUtil;
		final U zeroUtil = infeasibleUtil.getZero();
		
		int k;
		U prob;
		
		for (i = 0; myIter.hasNext(); i++) {
			assert i < nbrVarsKeptUtils : i + " >= " + nbrVarsKeptUtils;
			
			// Initialize the best solution
			optSol = firstVal;
			optExpUtil = infeasibleUtil;
			
			// Go through all possible assignments to the varOut to find the optimal one
			for (j = 0; j < varOutDomSize; j++) {
				
				// Initialize the expected util
				expUtil = zeroUtil;
				
				// Go through all possible assignments to the randVars
				k = -1;
				while (++k < nbrRandUtils) {
					prob = probIter.nextUtility();
					myIter.nextSolution();
					expUtil = expUtil.add(myIter.getCurrentUtility(optExpUtil.subtract(expUtil).divide(prob), !maximum).multiply(prob));
					assert (maximum ? 	myIter.getCurrentUtility().compareTo(zeroUtil) <= 0 : 
										myIter.getCurrentUtility().compareTo(zeroUtil) >= 0): "Non-monotone problem";
					
					// No use to look at the remaining scenarios if the expected utility is already infeasible or sub-optimal
					if (expUtil.equals(infeasibleUtil) || 
						(maximum && expUtil.compareTo(optExpUtil) < 0) || 
						(!maximum && expUtil.compareTo(optExpUtil) > 0)) 
						break;
				}
				
				// Check if we have found a better solution
				if (k == nbrRandUtils) { // the iteration was not interrupted
					optSol = myIter.getCurrentSolution()[indexVarOut];
					optExpUtil = expUtil;
					continue;
				}
				
				// Finish iterating without computing utilities if the iteration was interrupted
				while (++k < nbrRandUtils) {
					myIter.nextSolution();
					assert maximum == (myIter.getCurrentUtility().compareTo(zeroUtil) <= 0) : "Non-monotone problem";
					probIter.nextSolution();
				}
			}
			
			// Record the best solution found
			tmp = new ArrayList<V> (1);
			tmp.add(optSol);
			optSols[i] = tmp;
			optUtils[i] = optExpUtil;
		}
				
		// Build and return the output
		if (nbrVarsKept > 0) {
			String[] varsKept = new String [nbrVarsKept];
			System.arraycopy(iterOrder, 0, varsKept, 0, nbrVarsKept);
			
			V[][] varsKeptDoms = (V[][]) Array.newInstance(domClass, nbrVarsKept);
			System.arraycopy(iterOrderDoms, 0, varsKeptDoms, 0, nbrVarsKept);
			
			return new ProjOutput<V, U> (this.newInstance(varsKept, varsKeptDoms, optUtils, infeasibleUtil), 
					new String[] { varOut }, 
					new BasicHypercube< V, ArrayList<V> > (varsKept, varsKeptDoms, optSols, null));
			
		} else 
			return new ProjOutput<V, U> (this.scalarHypercube(optUtils[0]), 
					new String[] { varOut }, 
					new ScalarBasicHypercube< V, ArrayList<V> > (optSols[0], null));
	}
	
	/** 
	 * @see UtilitySolutionSpace#sample(int) 
	 * @warning Assumes that the hypercube contains a single variable, and that utilities sum up to 1.0. 
	 */
	public Map<V, Double> sample(int nbrSamples) {
		
		assert this.variables.length == 1 : "Sampling of multi-variable spaces not yet implemented";
		
		HashMap<V, Double> out = new HashMap<V, Double> ();
		V[] dom = this.domains[0];
		
		// If nbrSamples == 0, this means we should return the true weights, without sampling
		if (nbrSamples == 0) {
			for (int i = 0; i < this.number_of_utility_values; i++) {
				double prob = this.values[i].doubleValue();
				if (prob > 0) 
					out.put(dom[i], prob);
			}
			return out;
		}
		
		// Aggregate all probabilities to form the cumulative law
		U[] cumul = this.values.clone();
		U sum = this.values[0];
		for (int i = 1; i < number_of_utility_values; i++) {
			sum = sum.add(this.values[i]);
			cumul[i] = sum;
		}
		
		// Generate all samples
		for (int i = 0; i < nbrSamples; i++) {
			
			// Generate a random cumulated probability
			U rand = this.infeasibleUtil.fromString(Double.toString(Math.random()));
			
			// Find the first value for the random variable whose cumulated probability is higher 
			for (int j = 0; j < dom.length; j++) {
				if (cumul[j].compareTo(rand) >= 0) {
					
					// Increment previous weight for this value
					V val = dom[j];
					Double weight = out.get(val);
					if (weight == null) { // first time we pick this value
						out.put(val, 1.0);
					} else 
						out.put(val, weight + 1.0);
					
					break;
				}
			}
		}
		
		return out;
	}
	
	/** @see BasicHypercube#changeVariablesOrder(java.lang.String[]) */
	public Hypercube< V, U > changeVariablesOrder( String[] variables_order ) {
		return (Hypercube<V, U>) super.changeVariablesOrder(variables_order);
	}
	
	/**Checks if this hypercube equals the one provided as a parameter (including
	 * the order of variables).
	 * @param o the hypercube to compare with
	 * @return true if this hypercube equals to the provided hypercube and false if else
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals( Object o ) {

		if (o == this)
			return true;
		
		if (!(o instanceof UtilitySolutionSpace))
			return false;
		
		UtilitySolutionSpace<V, U> space = (UtilitySolutionSpace<V, U>) o;
		
		// Check that the two spaces agree on the variables and their orders
		if (! Arrays.equals(this.variables, space.getVariables())) 
			return false;
		
		return this.equivalent(space);
	}
	
	
	
	/** Computes the union of two lists, perserving the order when possible
	 * @param list1 first list
	 * @param list2 second list
	 * @return the union of the two lists
	 * @note When the two lists disaggree on the order, \a list1 wins. When the two lists don't care, \a list2's elements have the priority.
	 * @author Thomas Leaute
	 */
	public static String[] union (String[] list1, String[] list2) {
		
		assert list1 != null && list2 != null : "Calling Hypercube.union() on a null list";
		
		if (list1.length == 0) {
			return list2.clone();
		} else if (list2.length == 0) {
			return list1.clone();
		}
		
		ArrayList<String> result = new ArrayList<String> (list1.length + list2.length);
		
		// First compute the order imposed by the two lists
		
		// To each element, associates the set of elements that are after it in the order
		HashMap<String, HashSet<String> > order = new HashMap<String, HashSet<String> > (list1.length + list2.length);
		
		// For each element in the first list
		for (int i = 0; i < list1.length; i++) {
			HashSet<String> afters = new HashSet<String> (list1.length - i + list2.length - 2);
			order.put(list1[i], afters);
			
			// For each following element in the first list
			for (int j = i + 1; j < list1.length; j++) {
				
				// Record that list1[i] comes before list1[j]
				afters.add(list1[j]);
			}
		}
		
		// For each element in the second list
		for (int i = 0; i < list2.length; i++) {
			
			// For each following element in the second list
			for (int j = i + 1; j < list2.length; j++) {
				
				String ei = list2[i];
				String ej = list2[j];
				
				// Check if we have not already imposed that list2[j] comes before list2[i]:
				HashSet<String> afters = order.get(ej);
				if (afters == null || ! afters.contains(ei)) {
					
					// Record that list2[i] comes before list2[j]
					afters = order.get(ei);
					if (afters == null) {
						afters = new HashSet<String> (list2.length - i - 1);
						order.put(ei, afters);
					}
					afters.add(ej);
				}
			}
		}
		
		// Now, iterate over the two lists
		int i1 = 0, i2 = 0;
		while (i1 < list1.length && i2 < list2.length) {
			String one = list1[i1];
			String two = list2[i2];
			
			if (result.contains(one)) {
				i1++;
				continue;
			} else if (result.contains(two)) {
				i2++;
				continue;
			}

			if (one == two) {
				result.add(one);
				i1++;
				i2++;
			} else if (order.get(one).contains(two)) { // list1[i1] must come before list2[i2]
				result.add(one);
				i1++;
			} else { // list2[i2] must come before list1[i1], or we don't care
				result.add(two);
				i2++;
			}
		}
		
		// Add remaining elements
		for ( ; i1 < list1.length; i1++) {
			String e = list1[i1];
			if (! result.contains(e)) {
				result.add(e);
			}
		}
		for ( ; i2 < list2.length; i2++) {
			String e = list2[i2];
			if (! result.contains(e)) {
				result.add(e);
			}
		}
		
		return (String[]) result.toArray(list1);
	}
	

	
	
	
	/** @return a shallow clone of this hypercube */
	@Override
	public Hypercube< V, U > clone () {
		return (Hypercube<V, U>) super.clone();
	}
	
	/** @see BasicHypercube#resolve() */
	@Override
	public Hypercube<V, U> resolve() {
		return this;
	}

	/** @see UtilitySolutionSpace#toHypercube() */
	public Hypercube<V, U> toHypercube() {
		return this.resolve();
	}
	
	/** @see UtilitySolutionSpace#isIncludedIn(UtilitySolutionSpace) */
	public boolean isIncludedIn(UtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return false;
	}

	


	/** @see BasicHypercube#join(SolutionSpace, java.lang.String[]) */
	@SuppressWarnings("unchecked")
	@Override
	public SolutionSpace<V> join(SolutionSpace<V> space, String[] total_variables) {
		
		if (space instanceof UtilitySolutionSpace)
			return join((UtilitySolutionSpace<V, U>)space, total_variables);
		
		///@todo Implement join if space is not UtilitySolutionSpace
		return null;
	}
	
	/** @see BasicHypercube#scalarHypercube(java.io.Serializable) */
	@SuppressWarnings("unchecked")
	@Override
	protected ScalarHypercube<V, U> scalarHypercube(U utility) {
		return new ScalarHypercube<V, U> (utility, this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass());
	}

	/** @see BasicHypercube#newInstance(java.lang.String[], V[][], Serializable[], Serializable) */
	@SuppressWarnings("unchecked")
	@Override
	protected Hypercube<V, U> newInstance(String[] new_variables, V[][] new_domains, U[] new_values, U infeasibleUtil) {
		
		if (new_variables.length == 0) 
			return new ScalarHypercube<V, U> (new_values[0], infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass());
		
		return new Hypercube<V, U> ( new_variables, new_domains, new_values, infeasibleUtil );
	}
		
	/** @see BasicHypercube#compose(java.lang.String[], BasicUtilitySolutionSpace) */
	public UtilitySolutionSpace<V, U> compose(String[] varsOut, BasicUtilitySolutionSpace< V, ArrayList<V> > substitution) {
		return (UtilitySolutionSpace<V, U>) super.compose(varsOut, substitution);
	}
	
	/** @see UtilitySolutionSpace#iteratorBestFirst(boolean) */
	public UtilitySolutionSpace.IteratorBestFirst<V, U> iteratorBestFirst(boolean maximize) {
		return new HyperCubeIterBestFirst<V, U>(this, maximize);
	}
	
	/** @see BasicHypercube#iterator() */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator() {
		return (UtilitySolutionSpace.Iterator<V, U>) super.iterator();
	}
	
	/** @see BasicHypercube#sparseIter() */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter() {
		return (UtilitySolutionSpace.SparseIterator<V, U>) super.sparseIter();
	}
	
	/** @see BasicHypercube#iterator(java.lang.String[]) */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator(String[] order) {
		return (UtilitySolutionSpace.Iterator<V, U>) super.iterator(order);
	}
	
	/** @see BasicHypercube#sparseIter(java.lang.String[]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter(String[] order) {
		return (UtilitySolutionSpace.SparseIterator<V, U>) super.sparseIter(order);
	}
	
	/** @see BasicHypercube#iterator(java.lang.String[], V[][]) */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator(String[] variables, V[][] domains) {
		return (UtilitySolutionSpace.Iterator<V, U>) super.iterator(variables, domains);
	}

	/** @see BasicHypercube#sparseIter(java.lang.String[], V[][]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter(String[] variables, V[][] domains) {
		return (UtilitySolutionSpace.SparseIterator<V, U>) super.sparseIter(variables, domains);
	}

	/** @see BasicHypercube#iterator(java.lang.String[], V[][], V[]) */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator(String[] variables, V[][] domains, V[] assignment) {
		return (UtilitySolutionSpace.Iterator<V, U>) super.iterator(variables, domains, assignment);
	}

	/** @see BasicHypercube#sparseIter(java.lang.String[], V[][], V[]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter(String[] variables, V[][] domains, V[] assignment) {
		return (UtilitySolutionSpace.SparseIterator<V, U>) super.sparseIter(variables, domains, assignment);
	}

	/** @see BasicHypercube#newIter(java.lang.String[], V[][], V[], Serializable) */
	@Override
	protected UtilitySolutionSpace.Iterator<V, U> newIter (String[] variables, V[][] domains, V[] assignment, U skippedUtil) {
		
		if (variables == null) 
			return new HypercubeIter<V, U> (this, assignment, skippedUtil);
		else if (domains == null) 
			return new HypercubeIter<V, U> (this, variables, assignment, skippedUtil);
		else 
			return new HypercubeIter<V, U> (this, variables, domains, assignment, skippedUtil);
	}
	
	/**
	 * Class representing a NULL hypercube.
	 * @author  Ouaret Nacereddine, Thomas Leaute, Radoslaw Szymanek
	 * @param  < V  >  type used for the variable values
	 * @param  < U  >  type used for the utility values
	 */
	@SuppressWarnings("unchecked")
	public static class NullHypercube< V extends Addable<V>, U extends Addable<U> > 
	extends Hypercube< V, U > {
		
		/** The singleton NULL hypercube */
		@SuppressWarnings("rawtypes")
		public static final NullHypercube NULL = new NullHypercube();
		
		/** Constructor only used internally for externalization. Use the singleton NullHypercube.NULL if needed. */
		public NullHypercube() { }
		
		/** Does nothing
		 * @see BasicHypercube#setStepsHashmaps()
		 */
		@Override
		void setStepsHashmaps() { }
		
		/** Always returns 0
		 * @see BasicHypercube#getNumberOfSolutions()
		 */
		@Override
		public long getNumberOfSolutions () {
			return 0;
		}

		/** Always returns \c null
		 * @see BasicHypercube#getUtility(V[])
		 */
		@Override
		public U getUtility( V[] variables_values ) {
			return null;
		}
		
		/** Always returns \c null
		 * @see BasicHypercube#getUtility(java.lang.String[], V[])
		 */
		@Override
		public U getUtility( String[] variables_names, V[] variables_values ) {
			return null;
		}
		
		/** Always returns \c null
		 * @see BasicHypercube#getUtility(long)
		 */
		@Override
		public U getUtility( long index ){
			return null;
		}

		/** Does nothing
		 * @see BasicHypercube#setUtility(V[], java.io.Serializable)
		 */
		@Override
		public boolean setUtility (V[] variables_values, U utility) { return false; }
		
		/** Does nothing
		 * @see BasicHypercube#setUtility(long, java.io.Serializable)
		 */
		@Override 
		public void setUtility(long index, U utility) { }

		/** Always returns \c null
		 * @see BasicHypercube#getVariables()
		 */
		@Override
		public String[] getVariables() {
			return null;
		}
		
		/** Always returns 0
		 * @see BasicHypercube#getNumberOfVariables()
		 */
		@Override
		public int getNumberOfVariables () {
			return 0;
		}
		
		/** Always returns \c null
		 * @see BasicHypercube#getVariable(int)
		 */
		@Override
		public String getVariable( int index ) {
			return null;
		}
		
		/** Does nothing
		 * @see BasicHypercube#renameVariable(String, String) 
		 */
		@Override
		public void renameVariable (String oldName, String newName) { }
		
		/** @see BasicHypercube#renameAllVars(String[]) */
		@Override
		public NullHypercube<V, U> renameAllVars(String[] newVarNames) {
			return this;
		}

		/** Always returns -1
		 * @see BasicHypercube#getIndex(java.lang.String)
		 */
		@Override
		public int getIndex( String variable ) {
			return -1;
		}
		
		/** Always returns \c null
		 * @see BasicHypercube#getDomains()
		 */
		@Override
		public V[][] getDomains () {
			return null;
		}
		
		/** Always returns \c null
		 * @see BasicHypercube#getDomain(java.lang.String)
		 */
		@Override
		public V[] getDomain( String variable ){
			return null;
		}
		
		/** Always returns \c null
		 * @see BasicHypercube#getDomain(int)
		 */
		@Override
		public V[] getDomain( int index ) {
			return null;
		}
		
		/** Always returns \c null
		 * @see BasicHypercube#getDomain(java.lang.String, int)
		 */
		@Override
		public V[] getDomain( String variable, int index ) {
			return null;
		}
		
		/** Does nothing
		 * @see BasicHypercube#setDomain(String, V[]) 
		 */
		@Override
		public void setDomain (String var, V[] dom) { }
		
		/** @see BasicHypercube#toString() */
		@Override
		public String toString() {
			return new String("NULL Hypercube");
		}
		
		/** @see Hypercube#saveAsXML(java.lang.String) */
		@Override
		public void saveAsXML( String file ) {
			//create the root element
			Element root_element = new Element( "decision_diagram" );
			root_element.setText("NULL Hypercube");
			
			Document document = new Document( root_element );
		    try {
		      XMLOutputter output = new XMLOutputter( Format.getPrettyFormat() );
			  output.output( document, new FileOutputStream( file ) );
		    }
		    catch (IOException e) {
		      System.err.println( e.getMessage() );
		    }
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#join(UtilitySolutionSpace, java.lang.String[], boolean, boolean)
		 */
		@Override
		protected Hypercube.NullHypercube<V, U> join( UtilitySolutionSpace< V, U> hypercube, String[] total_variables, boolean addition, boolean minNCCCs ) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#join(UtilitySolutionSpace, boolean, boolean)
		 */
		@Override
		public Hypercube.NullHypercube<V, U> join( UtilitySolutionSpace< V, U> hypercube, boolean addition, boolean minNCCCs ) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#join(UtilitySolutionSpace, java.lang.String[])
		 */
		@Override
		public Hypercube.NullHypercube<V, U> join( UtilitySolutionSpace< V, U> hypercube, String[] total_variables ) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#join(UtilitySolutionSpace)
		 */
		@Override
		public Hypercube.NullHypercube<V, U> join( UtilitySolutionSpace< V, U> hypercube ) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#join(UtilitySolutionSpace[], boolean, boolean) 
		 */
		@Override
		protected Hypercube.NullHypercube<V, U> join(UtilitySolutionSpace<V, U>[] spaces, final boolean addition, boolean minNCCCs) {
			return NULL;
		}
		
		/** Always returns \a NULL 
		 * @see Hypercube#multiply(UtilitySolutionSpace, java.lang.String[]) 
		 */
		@Override
		public Hypercube.NullHypercube<V, U> multiply(UtilitySolutionSpace<V, U> space, String[] total_variables) {
			return NULL;
		}

		/** Always returns \a NULL 
		 * @see Hypercube#multiply(UtilitySolutionSpace) 
		 */
		@Override
		public Hypercube.NullHypercube<V, U> multiply(UtilitySolutionSpace<V, U> space) {
			return NULL;
		}
		
		
		/** Always returns \a NULL 
		 * @see Hypercube#newInstance(java.lang.String[], V[][], U[], Addable) 
		 */
		protected NullHypercube<V, U> newInstance(String[] new_variables, V[][] new_domains, U[] new_values, U infeasibleUtil) {
			return NULL;
		}
		
		
		/** Always returns \a NULL for the resulting hypercube, and for the optimal assignments
		 * @see Hypercube#consensus(java.lang.String, java.util.Map, boolean, boolean, boolean) 
		 */
		@Override
		protected ProjOutput< V, U > consensus (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum, boolean allSolutions, boolean expect) {
			return new ProjOutput<V, U> (NULL, new String [0], NULL);
		}

		/** Always returns \a NULL for the resulting hypercube, and for the optimal assignments
		 * @see Hypercube#project(java.lang.String[], boolean)
		 */
		@Override
		public ProjOutput< V, U > project( String[] variables_names, boolean maximum ) {
			return new ProjOutput<V, U> (NULL, new String [0], NULL);
		}
		
		/** Always returns \a NULL for the resulting hypercube, and for the optimal assignments
		 * @see Hypercube#project(java.lang.String, boolean)
		 */
		@Override
		public ProjOutput< V, U > project( String variable_name, boolean maximum ) {
			return new ProjOutput<V, U> (NULL, new String [0], NULL);
		}
		
		/** Always returns \a NULL for the resulting hypercube, and for the optimal assignments
		 * @see Hypercube#project(int, boolean)
		 */
		@Override
		public ProjOutput< V, U > project( int number_to_project, boolean maximum) {
			return new ProjOutput<V, U> (NULL, new String [0], NULL);
		}
		
		/** Always returns \a NULL for the resulting hypercube, and for the optimal assignments
		 * @see Hypercube#projectAll(boolean) 
		 */
		@Override
		public ProjOutput<V, U> projectAll(boolean maximum) {
			return new ProjOutput<V, U> (NULL, new String [0], NULL);
		}
		
		/** @see Hypercube#blindProject(java.lang.String, boolean) */
		@Override
		public Hypercube.NullHypercube<V, U> blindProject (String varOut, boolean maximize) {
			return NULL;
		}
		
		/** @see Hypercube#blindProject(java.lang.String[], boolean) */
		@Override
		public Hypercube.NullHypercube<V, U> blindProject (String[] varsOut, boolean maximize) {
			return NULL;
		}

		/** @see Hypercube#blindProjectAll(boolean) */
		@Override
		public U blindProjectAll (boolean maximize) {
			return super.infeasibleUtil;
		}

		/** @see Hypercube#min(java.lang.String) */
		@Override
		public Hypercube.NullHypercube<V, U> min (String var) {
			return NULL;
		}
		
		/** @see Hypercube#max(java.lang.String) */
		@Override
		public Hypercube.NullHypercube<V, U> max (String var) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#slice(java.lang.String, Addable)
		 */
		@Override
		public Hypercube.NullHypercube<V, U> slice ( String var, V val ) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#slice(java.lang.String[], V[][])
		 */
		@Override
		public Hypercube.NullHypercube<V, U> slice( String[] variables_names, V[][] sub_domains ) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#slice(V[])
		 */
		@Override
		public Hypercube.NullHypercube<V, U> slice( V[] variables_values ) {
			return NULL;
		}
		
		/** Always returns \a NULL 
		 * @see BasicHypercube#compose(java.lang.String[], BasicUtilitySolutionSpace) 
		 */
		public Hypercube.NullHypercube<V, U> compose(String[] vars, BasicUtilitySolutionSpace< V, ArrayList<V> > substitution) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#split(Addable, boolean)
		 */
		@Override
		public Hypercube.NullHypercube< V, U > split( U threshold, boolean maximum ) {
			return NULL;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#changeVariablesOrder(java.lang.String[])
		 */
		@Override
		public Hypercube.NullHypercube<V, U> changeVariablesOrder( String[] variables_order ) {
			return NULL;
		}
		
		/** @see Hypercube#equivalent(BasicUtilitySolutionSpace) */
		@Override
		public boolean equivalent( BasicUtilitySolutionSpace< V, U> hypercube ) {
			if( hypercube == NULL)
				return true;
			return false;
		}
		
		/** @see Hypercube#equals(Object) */		
		@Override
		public boolean equals( Object hypercube ) {
			if( hypercube == NULL)
				return true;
			return false;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#clone()
		 */
		@Override
		public Hypercube.NullHypercube< V, U > clone () {
			return NULL;
		}
		
		
		/** @see Hypercube#iterator() */
		@Override
		public HypercubeIter<V, U> iterator() {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}
		
		/** @see Hypercube#iterator(String[]) */
		@Override
		public HypercubeIter<V, U> iterator(String[] order) {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}
		
		/** @see BasicHypercube#iterator(String[], V[][]) */
		@Override
		public HypercubeIter<V, U> iterator(String[] variables, V[][] domains) {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}

		/** @see Hypercube#iterator(java.lang.String[], V[][], V[]) */
		@Override
		public HypercubeIter<V, U> iterator(String[] variables, V[][] domains, V[] assignment) {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}

		/** @see Hypercube#sparseIter() */
		@Override
		public HypercubeIter<V, U> sparseIter() {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}
		
		/** @see Hypercube#sparseIter(String[]) */
		@Override
		public HypercubeIter<V, U> sparseIter(String[] order) {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}
		
		/** @see BasicHypercube#sparseIter(String[], V[][]) */
		@Override
		public HypercubeIter<V, U> sparseIter(String[] variables, V[][] domains) {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}

		/** @see Hypercube#sparseIter(java.lang.String[], V[][], V[]) */
		@Override
		public HypercubeIter<V, U> sparseIter(String[] variables, V[][] domains, V[] assignment) {
			/// @todo Auto-generated method stub
			assert false : "not implemented!";
			return null;
		}

		
			
		/** Does nothing 
		 * @see BasicHypercube#augment(V[]) 
		 */
		public void augment( V[] variables_values ) { }

		
		/** Always returns \a NULL 
		 * @see Hypercube#join(SolutionSpace, java.lang.String[]) 
		 */
		@Override
		public Hypercube.NullHypercube<V, U> join( SolutionSpace< V> space, String[] total_variables ) {
			return NULL;
		}
		
		/** Always returns \a NULL 
		 * @see BasicHypercube#join(SolutionSpace) 
		 */
		@Override
		public Hypercube.NullHypercube<V, U> join( SolutionSpace< V > space) {
			return NULL;
		}

		/** Always returns \a NULL 
		 * @see BasicHypercube#join(SolutionSpace[], java.lang.String[]) 
		 */
		@Override
		public Hypercube.NullHypercube<V, U> join( SolutionSpace< V >[] spaces, String[] total_variables_order ) {
			return NULL;
		}
		
		/** Always returns \c false 
		 * @see Hypercube#isIncludedIn(UtilitySolutionSpace) 
		 */
		@Override
		public boolean isIncludedIn( UtilitySolutionSpace< V, U > space ) {
			return false;
		}
		
		/** Always returns \a NULL
		 * @see Hypercube#expectation(java.util.Map) 
		 */
		@Override
		public Hypercube.NullHypercube<V, U> expectation(Map< String, UtilitySolutionSpace<V, U> > distributions) {
			return NULL;		
		}

		/** Returns an empty sample set
		 * @see Hypercube#sample(int) 
		 */
		@Override 
		public Map<V, Double> sample(int nbrSamples) {
			return new HashMap<V, Double> (0);
		}
		
		/** Returns \c null 
		 * @see BasicHypercube#getDefaultUtility() 
		 */
		@Override
		public U getDefaultUtility() {
			return null;
		}

		/** Does nothing 
		 * @see BasicHypercube#setDefaultUtility(java.io.Serializable) 
		 */
		@Override
		public void setDefaultUtility(U utility) { }
		
		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException { }
				
		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException { }

		/** Method to deserialize the object in such a way that the singleton property is retained.
		 * @return singleton object*/
		protected Object readResolve() {
            return NULL;
        }
				
	}
	
	/** @see UtilitySolutionSpace#iteratorBestFirst(boolean, java.lang.String[], V[]) */
	public UtilitySolutionSpace.IteratorBestFirst<V, U> iteratorBestFirst(
			boolean maximize, String[] fixedVariables, V[] fixedValues) {
		/// @todo Auto-generated method stub
		assert false : "NotImplemented";
		return null;
	}
	
	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#rescale(frodo2.solutionSpaces.Addable, frodo2.solutionSpaces.Addable)
	 */
	@Override
	public UtilitySolutionSpace<V, U> rescale(U add, U multiply) {
		// TODO Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}
	
}
