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

package frodo2.algorithms.mpc_discsp;

import java.math.BigInteger;
import java.util.Arrays;

/** A matrix over a finite field
 * @author Thomas Leaute
 * @see "http://math.nist.gov/javanumerics/jama/"
 */
public class Matrix {

	/** The coefficients */
	protected BigInteger[][] A;

	/** The number of rows */
	protected final int m;
	
	/** The number of columns */
	protected final int n;

	/** The prime modulus used for finite-field arithmetic */
	protected final BigInteger mod;

	/** Constructor
	 * @param A 	The coefficients
	 * @param mod 	The prime modulus
	 */
	public Matrix (BigInteger[][] A, BigInteger mod) {
		m = A.length;
		n = A[0].length;
		this.A = A;
		this.mod = mod;
	}

	/** Constructor
	 * @param m 	The number of rows
	 * @param n 	The number of columns
	 * @param mod 	The prime modulus
	 */
	public Matrix (int m, int n, BigInteger mod) {
		this.m = m;
		this.n = n;
		A = new BigInteger[m][n];
		this.mod = mod;
	}

	/** Constructs a row vector
	 * @param v 			the elements
	 * @param costModulo 	the prime modulo
	 */
	public Matrix(BigInteger[] v, BigInteger costModulo) {
		this(new BigInteger[][] { v }, costModulo);
	}

	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return Arrays.deepToString(this.A);
	}

	/** @return the inverse of this matrix, assuming it exists */
	public Matrix inverse () {
		return solve(identity(m,m, mod));
	}

	/** Generates an identity matrix
	 * @param m 	The number of rows
	 * @param n 	The number of columns
	 * @param mod 	The prime modulus
	 * @return identity matrix
	 */
	public static Matrix identity (final int m, final int n, BigInteger mod) {

		Matrix A = new Matrix(m,n, mod);
		BigInteger[][] X = A.getArray();
		BigInteger[] Xi;
		for (int i = 0; i < m; i++) {
			Xi = X[i];
			Arrays.fill(Xi, BigInteger.ZERO);
			Xi[i] = BigInteger.ONE;
		}
		return A;
	}

	/** @return the coefficients */
	public BigInteger[][] getArray () {
		return A;
	}

	/** @return a deep copy of the coefficients */
	public BigInteger[][] getArrayCopy () {
		BigInteger[][] C = new BigInteger[m][n];
		for (int i = 0; i < m; i++) 
			System.arraycopy(A[i], 0, C[i], 0, n);
		return C;
	}

	/** Solves A*X = B, assuming this matrix is square and invertible
	 * @param B 	right-hand-side matrix
	 * @return X
	 */
	public Matrix solve (Matrix B) {
		assert this.m == this.n : "unsupported";
		return new LUdecomposition(this, mod).solve(B);
	}

	/** @return the number of rows */
	public int getRowDimension () {
		return m;
	}

	/** @return the number of columns */
	public int getColumnDimension () {
		return n;
	}

	/** Get a submatrix.
	 * @param r 	Array of row indices.
	 * @param j0 	Initial column index
	 * @param j1 	Final column index
	 * @return A(r(:),j0:j1)
	 */
	public Matrix getMatrix (final int[] r, final int j0, final int j1) {
		Matrix X = new Matrix(r.length,j1-j0+1, mod);
		BigInteger[][] B = X.getArray();
		try {
			for (int i = 0; i < r.length; i++) 
				System.arraycopy(A[r[i]], j0, B[i], 0, j1-j0+1);
		} catch(ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
		return X;
	}

	/** Multiplication
	 * @param B 	the other matrix
	 * @return A*B
	 */
	public Matrix times (final Matrix B) {

		Matrix X = new Matrix(m,B.n, mod);
		BigInteger[][] C = X.getArray();
		BigInteger[] Bcolj = new BigInteger[n];
		for (int j = 0; j < B.n; j++) {
			for (int k = 0; k < n; k++) {
				Bcolj[k] = B.A[k][j];
			}
			for (int i = 0; i < m; i++) {
				BigInteger[] Arowi = A[i];
				BigInteger s = BigInteger.ZERO;
				for (int k = 0; k < n; k++) {
					s = s.add(Arowi[k].multiply(Bcolj[k])).mod(mod);
				}
				C[i][j] = s;
			}
		}
		return X;
	}

	/** Sets a coefficient
	 * @param i 	row number
	 * @param j 	column number
	 * @param s 	value
	 */
	public void set (int i, int j, BigInteger s) {
		A[i][j] = s;
	}

}
