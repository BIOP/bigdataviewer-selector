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

import bdv.tools.boundingbox.RenderBoxHelper;
import bdv.tools.boundingbox.TransformedBox;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Displays names on top of visible sources of all visible {@link SourceAndConverter} of a
 * {@link ViewerPanel}
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 */

public class SourceNameOverlay extends BdvOverlay {
	final ViewerPanel viewer;
	private int canvasWidth;
	private int canvasHeight;

	private final Object lockSourceBoxOverlay = new Object();
	private volatile List<SourceBoxOverlay> sourcesBoxOverlay = new ArrayList<>();

	final Map<String, OverlayStyle> styles = new HashMap<>();

	public SourceNameOverlay(ViewerPanel viewer)
	{
		this.viewer = viewer;
		updateBoxes();
		styles.put("DEFAULT", new DefaultOverlayStyle());
	}

	public Map<String, OverlayStyle> getStyles() {
		return styles;
	}

	@Override
	public synchronized void draw(Graphics2D g) {

		// The list of sources is rebuilt at every repaint : contrary to
		// SourceSelectorOverlay, this overlay is not driven by a
		// ViewerStateChangeListener, so a cached list would keep displaying the
		// names of sources which have since been removed or hidden, would miss the
		// sources added after this overlay was created, and would hold on to
		// removed sources. The cost is negligible next to the geometry computed
		// below for each source.
		updateBoxes();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Map<Integer,Set<Integer>> occupied = new HashMap<>();

		for (SourceBoxOverlay source : sourcesBoxOverlay) {
			source.drawSourceNameOverlay(g, occupied);
		}

	}

	@Override
	public void setCanvasSize(final int width, final int height) {
		this.canvasWidth = width;
		this.canvasHeight = height;
	}

	public void updateBoxes() {
		List<SourceBoxOverlay> newSourcesBoxOverlay = new ArrayList<>();

		int currentTimepoint = viewer.state().getCurrentTimepoint();
		for (SourceAndConverter<?> sac : viewer.state().getVisibleSources()) {
			// Sources absent at the current timepoint are labelled nowhere anyway
			// (getInterval returns null), testing isPresent first only avoids
			// calling getSource on a timepoint it may not know about
			if (sac.getSpimSource().isPresent(currentTimepoint) && sac
				.getSpimSource().getSource(currentTimepoint,
					0) != null) { // TODO : that's a hack to prevent issues with overlay sources
				newSourcesBoxOverlay.add(new SourceNameOverlay.SourceBoxOverlay(
					sac));
			}
		}

		synchronized (lockSourceBoxOverlay) {
			sourcesBoxOverlay = newSourcesBoxOverlay;
		}

	}

	class SourceBoxOverlay implements TransformedBox {

		final SourceAndConverter<?> sac;

		final RenderBoxHelper rbh;

		public SourceBoxOverlay(SourceAndConverter<?> sac) {
			this.sac = sac;
			rbh = new RenderBoxHelper();
		}

		private	void drawSourceNameOverlay(Graphics2D graphics, Map<Integer,Set<Integer>> occupied) {
			OverlayStyle os;
		
			os = styles.get("DEFAULT");
			
			final GeneralPath front = new GeneralPath();
			final GeneralPath back = new GeneralPath();
			final GeneralPath intersection = new GeneralPath();

			final RealInterval interval = getInterval();
			if (interval != null) {
				final double ox = canvasWidth / 2.0;
				final double oy = canvasHeight / 2.0;
				AffineTransform3D viewerTransform = new AffineTransform3D();
				viewer.state().getViewerTransform(viewerTransform);
				AffineTransform3D transform = new AffineTransform3D();

				getTransform(transform);
				transform.preConcatenate(viewerTransform);

				rbh.setOrigin(ox, oy);
				rbh.setScale(1);

				rbh.renderBox(interval, transform, front, back, intersection);
				Rectangle screen = new Rectangle(0,0,canvasWidth, canvasHeight);
				Rectangle rectBounds = intersection.getBounds();
				if ((rectBounds.x + rectBounds.width > 0) &&
					(rectBounds.x < canvasWidth))
				{
					if ((rectBounds.y + rectBounds.height > 0) &&
						(rectBounds.y < canvasHeight))
					{
						Area a = new Area(intersection);
						a.intersect(new Area(screen));
						double cx = a.getBounds2D().getCenterX();
						double cy = a.getBounds2D().getCenterY();
						LabelRenderer.drawLabel(graphics, sac.getSpimSource().getName(),
							cx, cy, os.getFrontColor(), os.getTextOutlineColor(),
							occupied);
					}
				}
			}

		}

		@Override
		public RealInterval getInterval() {
			long[] dims = new long[3];
			int currentTimePoint = viewer.state().getCurrentTimepoint();
			if (sac.getSpimSource().isPresent(currentTimePoint)) {
				sac.getSpimSource().getSource(currentTimePoint, 0).dimensions(dims);
				return new FinalRealInterval(new double[] { -0.5, -0.5, -0.5 },
					new double[] { dims[0] - 0.5, dims[1] - 0.5, dims[2] - 0.5 });
			}
			else return null;
		}

		@Override
		public void getTransform(AffineTransform3D transform) {
			sac.getSpimSource().getSourceTransform(viewer.state()
				.getCurrentTimepoint(), 0, transform);
		}

	}

	/**
	 * STYLES
	 */

	public interface OverlayStyle {

		/**
		 * Color of the outline drawn around the source names, which keeps them
		 * readable over bright image data
		 */
		Color TEXT_OUTLINE_COLOR = new Color(0, 0, 0, 200);

		Color getBackColor();

		Color getFrontColor();

		Color getIntersectionColor();

		Color getIntersectionFillColor();

		Stroke getNormalStroke();

		Stroke getIntersectionStroke();

		/**
		 * @return the color of the outline drawn around the source names, or null
		 *         for no outline
		 */
		default Color getTextOutlineColor() {
			return TEXT_OUTLINE_COLOR;
		}

	}

	public static class DefaultOverlayStyle implements
		SourceNameOverlay.OverlayStyle
	{

		final Color backColor = new Color(0x00994499);

		// Light enough to be read over the black background of bdv, the previous
		// dark indigo was barely visible
		final Color frontColor = new Color(0xD9CFF2);

		final Color intersectionFillColor = new Color(0x32994499, true);

		final Stroke normalStroke = new BasicStroke();

		final Stroke intersectionStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f);

		final Color intersectionColor = Color.WHITE.darker();

		public Color getBackColor() {
			return backColor;
		}

		public Color getFrontColor() {
			return frontColor;
		}

		@Override
		public Color getIntersectionColor() {
			return intersectionColor;
		}

		public Color getIntersectionFillColor() {
			return intersectionFillColor;
		}

		public Stroke getNormalStroke() {
			return normalStroke;
		}

		@Override
		public Stroke getIntersectionStroke() {
			return intersectionStroke;
		}
	}

}
