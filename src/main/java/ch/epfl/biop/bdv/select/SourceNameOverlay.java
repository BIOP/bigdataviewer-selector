
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
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

	private List<SourceBoxOverlay> sourcesBoxOverlay = new ArrayList<>();

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

		for (SourceAndConverter<?> sac : viewer.state().getVisibleSources()) {
			if (sac.getSpimSource().getSource(viewer.state().getCurrentTimepoint(),
				0) != null) { // TODO : that's a hack to prevent issues with overlay sources
				newSourcesBoxOverlay.add(new SourceNameOverlay.SourceBoxOverlay(
					sac));
			}
		}

		synchronized (sourcesBoxOverlay) {
			sourcesBoxOverlay = newSourcesBoxOverlay;
		}

	}

	private static Map<Integer,Set<Integer>> displayNameAt(SourceAndConverter<?> sac, Graphics2D graphics, double xp, double yp, String name, Map<Integer,Set<Integer>> occupied) {
		double binSizeX = 100;
		double binSizeY = 20;
		int binX = (int) (xp/binSizeX);
		int binY = (int) (yp/binSizeY);
		int shiftX = 0;
		int shiftY = 0;
		if (!occupied.containsKey(binX)) {
			occupied.put(binX, new HashSet<>());
		}
		Set<Integer> occupiedY = occupied.get(binX);

		while (occupiedY.contains(binY)) {
			binY++;
			shiftY += (int) binSizeY;
		}
		occupiedY.add(binY);
		graphics.drawString(sac.getSpimSource().getName(),(int) (xp+shiftX),(int) (yp+shiftY));
		return occupied;
	}

	class SourceBoxOverlay implements TransformedBox {

		final SourceAndConverter<?> sac;

		final RenderBoxHelper rbh;

		public SourceBoxOverlay(SourceAndConverter<?> sac) {
			this.sac = sac;
			rbh = new RenderBoxHelper();
		}

		private	Map<Integer,Set<Integer>> drawSourceNameOverlay(Graphics2D graphics, Map<Integer,Set<Integer>> occupied) {
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
						//graphics.drawString(sac.getSpimSource().getName(),(int) cx,(int)cy);
						graphics.setColor(os.getFrontColor());
						occupied = displayNameAt(sac, graphics, cx, cy, sac.getSpimSource().getName(), occupied);
					}
				}
			}

			return occupied;

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

		Color getBackColor();

		Color getFrontColor();

		Color getIntersectionColor();

		Color getIntersectionFillColor();

		Stroke getNormalStroke();

		Stroke getIntersectionStroke();

	}

	public static class DefaultOverlayStyle implements
		SourceNameOverlay.OverlayStyle
	{

		final Color backColor = new Color(0x00994499);

		final Color frontColor = new Color(0x5D46B6);

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
