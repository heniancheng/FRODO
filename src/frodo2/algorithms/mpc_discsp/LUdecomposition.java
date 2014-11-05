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

/** LU decomposition of a matrix
 * @author Thomas Leaute
 * @see "http://math.nist.gov/javanumerics/jama/"
 */
public class LUdecomposition extends Matrix {

	/** The pivot sign(?) */
	private int pivsign; 

	/** The pivot(?) */
	private final int[] piv;
	
	/** Constructor
	 * @param M 	the reference matrix
	 * @param mod 	the prime modulus
	 */
	public LUdecomposition (Matrix M, BigInteger mod) {
		super (M.getArrayCopy(), mod);
		
		// Use a "left-looking", dot-product, Crout/Doolittle algorithm.

		piv = new int[m];
		for (int i = 0; i < m; i++) {
			piv[i] = i;
		}
		pivsign = 1;
		BigInteger[] LUrowi;
		BigInteger[] LUcolj = new BigInteger[m];

		// Outer loop.
		for (int j = 0; j < n; j++) {

			// Make a copy of the j-th column to localize references.
			for (int i = 0; i < m; i++) 
				LUcolj[i] = A[i][j];

			// Apply previous transformations.
			for (int i = 0; i < m; i++) {
				LUrowi = A[i];

				// Most of the time is spent in the following dot product.
				final int kmax = Math.min(i,j);
				BigInteger s = BigInteger.ZERO;
				for (int k = 0; k < kmax; k++) 
					s = s.add(LUrowi[k].multiply(LUcolj[k])).mod(mod);

				LUcolj[i] = LUcolj[i].subtract(s).mod(mod);
				LUrowi[j] = LUcolj[i];
			}

			// Find pivot and exchange if necessary.
			int p = j;
			for (int i = j+1; i < m; i++) 
				if (LUcolj[i].abs().compareTo(LUcolj[p].abs()) > 0) /// @bug Is this guaranteed to work modulo mod?
					p = i;
			if (p != j) {
				for (int k = 0; k < n; k++) {
					BigInteger t = A[p][k]; A[p][k] = A[j][k]; A[j][k] = t;
				}
				int k = piv[p]; piv[p] = piv[j]; piv[j] = k;
				pivsign = -pivsign;
			}

			// Compute multipliers.
			if (j < m & ! A[j][j].equals(BigInteger.ZERO)) 
				for (int i = j+1; i < m; i++) 
					A[i][j] = A[i][j].multiply(A[j][j].modInverse(mod));
		}
	}

	/** @see Matrix#solve(Matrix) */
	@Override
	public Matrix solve (final Matrix B) {
		assert B.getRowDimension() == m : "Matrix row dimensions must agree.";

		// Copy right hand side with pivoting
		final int nx = B.getColumnDimension();
		Matrix Xmat = B.getMatrix(piv,0,nx-1);
		BigInteger[][] X = Xmat.getArray();

		// Solve L*Y = B(piv,:)
		BigInteger[] Xk, Xi, LUi;
		for (int k = 0; k < n; k++) {
			Xk = X[k];
			for (int i = k+1; i < n; i++) {
				Xi = X[i];
				LUi = A[i];
				for (int j = 0; j < nx; j++) 
					Xi[j] = Xi[j].subtract(Xk[j].multiply(LUi[k]).mod(mod));
			}
		}
		
		// Solve U*X = Y;
		BigInteger LUkkInv, LUik;
		for (int k = n-1; k >= 0; k--) {
			Xk = X[k];
			LUkkInv = A[k][k].modInverse(mod);
			for (int j = 0; j < nx; j++) 
				Xk[j] = Xk[j].multiply(LUkkInv).mod(mod);
			for (int i = 0; i < k; i++) {
				Xi = X[i];
				LUik = A[i][k];
				for (int j = 0; j < nx; j++) 
					Xi[j] = Xi[j].subtract(Xk[j].multiply(LUik)).mod(mod);
			}
		}
		return Xmat;
	}

}
