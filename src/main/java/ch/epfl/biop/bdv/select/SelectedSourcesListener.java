/*-
 * #%L
 * Selector Behaviours for BigDataViewer
 * %%
 * Copyright (C) 2020 - 2026 BIOP (BioImaging & Optics Platform), EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

package ch.epfl.biop.bdv.select;

import bdv.viewer.SourceAndConverter;
import java.util.Collection;
import java.util.Set;

/**
 * Interface to respond to source selection changes
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL
 */

public interface SelectedSourcesListener {

	/**
	 * Function triggered first when the selected sources are changed
	 * 
	 * @param selectedSources : the new list of selected sources
	 * @param triggerMode : string which indicates the origin of the event that
	 *          triggered a change see
	 *          {@link SourceSelectorBehaviour#processSelectionModificationEvent(Set, String, String)}
	 */
	void selectedSourcesUpdated(Collection<SourceAndConverter<?>> selectedSources,
		String triggerMode);

	/**
	 * Function triggered second and which specifies what sources have had their
	 * status changed This makes it easier to follow the successive steps during
	 * consecutive selection events
	 * 
	 * @param lastSelectedSources : the list of sources which have been involved
	 *          in the last selection event
	 * @param mode : should be {@link SourceSelectorBehaviour#SET} or
	 *          {@link SourceSelectorBehaviour#ADD} or
	 *          {@link SourceSelectorBehaviour#REMOVE}
	 * @param triggerMode string which indicates the origin of the event that
	 *          triggered a change see
	 *          {@link SourceSelectorBehaviour#processSelectionModificationEvent(Set, String, String)}
	 */
	void lastSelectionEvent(Collection<SourceAndConverter<?>> lastSelectedSources,
		String mode, String triggerMode);

}
