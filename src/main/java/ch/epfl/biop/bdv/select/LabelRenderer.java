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

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Draws the name of a source, centered on a point of the canvas, outlined so
 * that it stays readable whatever is painted below it, and moved down when
 * another name has already been drawn at the same place.
 * <p>
 * Shared by {@link SourceSelectorOverlay} and {@link SourceNameOverlay}, which
 * both label the sources they display in the same way.
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL
 */
class LabelRenderer {

	/**
	 * Size in pixels of a cell of the occupancy grid used to avoid drawing two
	 * names on top of each other.
	 */
	private static final int BIN_SIZE_X = 100;

	private static final int BIN_SIZE_Y = 20;

	private LabelRenderer() {}

	/**
	 * Draws {@code label} centered on ({@code xCenter}, {@code yCenter}), unless
	 * that spot is already taken according to {@code occupied}, in which case the
	 * label is pushed down by as many rows as needed.
	 *
	 * @param graphics the canvas to draw on
	 * @param label the text to draw, typically the name of a source
	 * @param xCenter x coordinate the label should be centered on
	 * @param yCenter y coordinate the label should be centered on
	 * @param textColor color of the text
	 * @param outlineColor color of the one pixel outline drawn around the text,
	 *          or null for no outline
	 * @param occupied grid cells already taken by the labels drawn before this
	 *          one, updated by this call
	 */
	static void drawLabel(Graphics2D graphics, String label, double xCenter,
		double yCenter, Color textColor, Color outlineColor,
		Map<Integer, Set<Integer>> occupied)
	{
		final FontMetrics fm = graphics.getFontMetrics();
		final int width = fm.stringWidth(label);

		// drawString anchors the text at the left end of its baseline, so both
		// coordinates have to be shifted to actually center the label
		final int x = (int) Math.round(xCenter - width / 2.0);
		final int baseline = (int) Math.round(yCenter + (fm.getAscent() - fm
			.getDescent()) / 2.0);

		// Push the label down until it finds a free row. Every column the label
		// spans is checked, so that a long name does not overlap the names of its
		// neighbours.
		final int binXFrom = Math.floorDiv(x, BIN_SIZE_X);
		final int binXTo = Math.floorDiv(x + width, BIN_SIZE_X);
		final int binYStart = Math.floorDiv(baseline, BIN_SIZE_Y);
		int binY = binYStart;
		while (isOccupied(occupied, binXFrom, binXTo, binY)) {
			binY++;
		}
		for (int binX = binXFrom; binX <= binXTo; binX++) {
			occupied.computeIfAbsent(binX, k -> new HashSet<>()).add(binY);
		}
		final int y = baseline + (binY - binYStart) * BIN_SIZE_Y;

		// The outline keeps the name readable over the black background as well as
		// over bright image data
		if (outlineColor != null) {
			graphics.setColor(outlineColor);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if ((dx != 0) || (dy != 0)) {
						graphics.drawString(label, x + dx, y + dy);
					}
				}
			}
		}

		graphics.setColor(textColor);
		graphics.drawString(label, x, y);
	}

	private static boolean isOccupied(Map<Integer, Set<Integer>> occupied,
		int binXFrom, int binXTo, int binY)
	{
		for (int binX = binXFrom; binX <= binXTo; binX++) {
			final Set<Integer> rows = occupied.get(binX);
			if ((rows != null) && rows.contains(binY)) return true;
		}
		return false;
	}

}
