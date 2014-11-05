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

package frodo2.algorithms;

import java.util.ArrayList;
import java.util.HashMap;

import frodo2.algorithms.StatsReporterWithConvergence.CurrentAssignment;
import frodo2.solutionSpaces.Addable;

/** Interface for all modules that should be able to store convergence data
 * @author Brammert Ottens, 30 nov 2010
 * @param <V> type used for domain values
 * 
 */
public interface ConvergenceInterface <V extends Addable<V>> {

	/**
	 * @author Brammert Ottens, 30 nov 2010
	 * @return the history of variable assignments
	 */
	public HashMap<String, ArrayList<CurrentAssignment<V>>> getAssignmentHistories();
}
