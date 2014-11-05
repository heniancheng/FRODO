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

package frodo2.solutionSpaces.vehiclerouting;

import frodo2.solutionSpaces.AddableInteger;
import com.orllc.orobjects.lib.geom.rect2.Point;
import com.orllc.orobjects.lib.real.DoubleI;

/** A customer in the Vehicle Routing Problem
 * @author Thomas Leaute
 */
public class Customer extends Point implements DoubleI {
	
	/** Used for serialization */
	private static final long serialVersionUID = -5719623691432541492L;

	/** The customer ID */
	final int id;
	
	/** The current customer demand */
	int demand;
	
	/** The initial X coordinate */
	private final double xInit;
	
	/** The current X coordinate */
	private double x;
	
	/** The initial Y coordinate */
	private final double yInit;

	/** The current Y coordinate */
	private double y;
	
	/** The radius of uncertainty on the position of this customer */
	private final double radius;
	
	/** The possible angles for the position of this customer on the circle centered at (xInit, yInit) or radius this.radius */
	AddableInteger[] angles;

	/** Constructor
	 * @param id 		The customer ID
	 * @param demand 	The customer demand
	 * @param x 		The X coordinate
	 * @param y 		The Y coordinate
	 * @param radius 	The radius of uncertainty on the position of this customer
	 * @param angles 	The possible angles for the position of this customer on the circle centered at (xInit, yInit) or radius this.radius
	 */
	public Customer(int id, int demand, double x, double y, double radius, AddableInteger[] angles) {
		super (x, y);
		this.id = id;
		this.demand = demand;
		this.xInit = x;
		this.x = x;
		this.yInit = y;
		this.y = y;
		this.radius = radius;
		this.angles = angles;
	}

	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "Customer(id = " + this.id + "\tdemand = " + this.demand + "\tx = " + this.x + "\ty = " + this.y + ")";
	}

	/** @see DoubleI#doubleValue() */
	public double doubleValue() {
		return this.demand;
	}
	
	/** @see java.lang.Object#clone() */
	@Override
	public Customer clone () {
		return new Customer (this.id, this.demand, this.x, this.y, this.radius, this.angles);
	}
	
	/** @see Point#equals(java.lang.Object) */
	@Override
	public boolean equals (Object o) {
		
		if (this == o) 
			return true;
		
		try {
			Customer that = (Customer) o;
			
			return this.id == that.id;
			
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	/** @see Point#hashCode() */
	@Override
	public int hashCode () {
		return this.id;
	}
	
	/** Computes the exact position of the customer
	 * @param angle 	the positional angle of the customer on the circle or radius this.radius centered at (this.xInit, this.yInit)
	 */
	void setPosition (int angle) {
		
		/// @bug Does not change the coordinates from the point of view of the super class
		
		double angleInRad = 2.0 * Math.PI / this.angles.length * angle;
		this.x = this.xInit + this.radius * Math.cos(angleInRad);
		this.y = this.yInit + this.radius * Math.sin(angleInRad);
	}
	
}
