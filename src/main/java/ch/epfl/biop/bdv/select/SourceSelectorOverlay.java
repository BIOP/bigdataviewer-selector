
package ch.epfl.biop.bdv.select;

import bdv.tools.boundingbox.TransformedBox;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.tools.boundingbox.RenderBoxHelper;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
 * Works with {@link SourceSelectorBehaviour} Displays box overlays on top of
 * visible sources of all visible {@link SourceAndConverter} of a
 * {@link ViewerPanel} The coloring differs depending on the state (selected or
 * not selected) An inner interface {@link OverlayStyle} is used to define how
 * the overlay box are colored. The coloring can be modified with the
 * {@link SourceSelectorOverlay#getStyles()} function and by modify using the
 * values contained in "DEFAULT" and "SELECTED" GUI functioning: The user can
 * draw a rectangle and all sources which intersects this rectangle AT THE
 * CURRENT PLANE SLICING of Bdv will be involved in the next selection change
 * event. Either the user was holding no extra key: - the involved sources will
 * define the new selection set The user was holding CTRL: - the involved
 * sources will be removed from the current selection set The user was holding
 * SHIFT: - the involved sources are added to the current selection set Note :
 * changing the key pressing DURING the rectangle drawing will not be taken into
 * account, contrary to an expected standard behaviour TODO : can this be
 * improved ? Note : The user can perform a single click as well with the
 * modifier keys, no need to drag this is because a single click also triggers a
 * {@link DragBehaviour} Note : the overlay can be very slow to draw - because
 * it's java graphics 2D... It's especially visible is the zoom is very big...
 * Clipping is badly done TODO ?
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 */

public class SourceSelectorOverlay extends BdvOverlay {

	final ViewerPanel viewer;

	boolean isCurrentlySelecting = false;

	int xCurrentSelectStart, yCurrentSelectStart, xCurrentSelectEnd,
			yCurrentSelectEnd;

	private int canvasWidth;

	private int canvasHeight;

	private final Object lockSourceBoxOverlay = new Object();
	private List<SourceBoxOverlay> sourcesBoxOverlay = new ArrayList<>();

	final Map<String, OverlayStyle> styles = new HashMap<>();

	final SourceSelectorBehaviour ssb;

	boolean displaySourcesNames = true;

	public void showSourcesNames() {
		displaySourcesNames = true;
	}

	public void hideSourcesNames() {
		displaySourcesNames = false;
	}

	public SourceSelectorOverlay(ViewerPanel viewer,
		SourceSelectorBehaviour ssb)
	{
		this.ssb = ssb;
		this.viewer = viewer;
		updateBoxes();
		styles.put("DEFAULT", new DefaultOverlayStyle());
		styles.put("SELECTED", new SelectedOverlayStyle());
	}

	protected void addSelectionBehaviours(Behaviours behaviours) {
		behaviours.behaviour(new RectangleSelectSourcesBehaviour(
			SourceSelectorBehaviour.SET), "select-set-sources", "button1");
		behaviours.behaviour(new RectangleSelectSourcesBehaviour(
			SourceSelectorBehaviour.ADD), "select-add-sources", "shift button1");
		behaviours.behaviour(new RectangleSelectSourcesBehaviour(
			SourceSelectorBehaviour.REMOVE), "select-remove-sources", "ctrl button1");
		// Ctrl + A : select all sources
		behaviours.behaviour((ClickBehaviour) (x, y) -> ssb.selectedSourceAdd(viewer
			.state().getVisibleSources()), "select-all-visible-sources", "ctrl A");
	}

	public Map<String, OverlayStyle> getStyles() {
		return styles;
	}

	synchronized void startCurrentSelection(int x, int y) {
		xCurrentSelectStart = x;
		yCurrentSelectStart = y;
	}

	synchronized void updateCurrentSelection(int xCurrent, int yCurrent) {
		xCurrentSelectEnd = xCurrent;
		yCurrentSelectEnd = yCurrent;
		isCurrentlySelecting = true;
	}

	synchronized void endCurrentSelection(int x, int y, String mode) {
		xCurrentSelectEnd = x;
		yCurrentSelectEnd = y;
		isCurrentlySelecting = false;
		// Selection is done : but we need to access the trigger keys to understand
		// what's happening
		// Set<SourceAndConverter<?>> currentSelection =
		this.getLastSelectedSources();
		ssb.processSelectionModificationEvent(getLastSelectedSources(), mode,
			"SelectorOverlay");
	}

	Rectangle getCurrentSelectionRectangle() {
		int x0, y0, w, h;
		if (xCurrentSelectStart > xCurrentSelectEnd) {
			x0 = xCurrentSelectEnd;
			w = xCurrentSelectStart - xCurrentSelectEnd;
		}
		else {
			x0 = xCurrentSelectStart;
			w = xCurrentSelectEnd - xCurrentSelectStart;
		}
		if (yCurrentSelectStart > yCurrentSelectEnd) {
			y0 = yCurrentSelectEnd;
			h = yCurrentSelectStart - yCurrentSelectEnd;
		}
		else {
			y0 = yCurrentSelectStart;
			h = yCurrentSelectEnd - yCurrentSelectStart;
		}
		// Hack : allows selection on double or single click
		if (w == 0) w = 1;
		if (h == 0) h = 1;
		return new Rectangle(x0, y0, w, h);
	}

	synchronized Set<SourceAndConverter<?>> getLastSelectedSources() {
		Set<SourceAndConverter<?>> lastSelected = new HashSet<>();

		final RenderBoxHelper rbh = new RenderBoxHelper();

		// We need to find whether a rectangle in real space intersects a box in 3d
		// -> Makes use of the work previously done in RenderBoxHelper
		for (SourceBoxOverlay sbo : sourcesBoxOverlay) {
			//
			AffineTransform3D viewerTransform = new AffineTransform3D();
			viewer.state().getViewerTransform(viewerTransform);
			AffineTransform3D transform = new AffineTransform3D();

			sbo.getTransform(transform);
			transform.preConcatenate(viewerTransform);

			final double ox = canvasWidth / 2.0;
			final double oy = canvasHeight / 2.0;

			rbh.setOrigin(ox, oy);
			rbh.setScale(1);

			final GeneralPath front = new GeneralPath();
			final GeneralPath back = new GeneralPath();
			final GeneralPath intersection = new GeneralPath();

			if (sbo.getInterval() != null) {
				rbh.renderBox(sbo.getInterval(), transform, front, back, intersection);
				Rectangle r = getCurrentSelectionRectangle();

				if (intersection.intersects(r) || intersection.contains(r)) {
					lastSelected.add(sbo.sac);
				}
			}

		}
		return lastSelected;
	}

	@Override
	public synchronized void draw(Graphics2D g) {

		Map<Integer,Set<Integer>> occupied = new HashMap<>();

		for (SourceBoxOverlay source : sourcesBoxOverlay) {
			source.drawBoxOverlay(g, displaySourcesNames, occupied);
		}

		if (isCurrentlySelecting) {
			g.setStroke(styles.get("SELECTED").getNormalStroke());
			g.setPaint(styles.get("SELECTED").getBackColor());
			g.draw(getCurrentSelectionRectangle());
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
				0) != null)
			{ // TODO : fix hack to avoid dirty overlay filter
				newSourcesBoxOverlay.add(new SourceSelectorOverlay.SourceBoxOverlay(
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

		private Map<Integer,Set<Integer>> displayAt(Graphics2D graphics, double xp, double yp, String name, Map<Integer,Set<Integer>> occupied) {
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
				shiftY += binSizeY;
			}
			occupiedY.add(binY);
			graphics.drawString(sac.getSpimSource().getName(),(int) (xp+shiftX),(int) (yp+shiftY));
			return occupied;
		}

		private	Map<Integer,Set<Integer>> drawBoxOverlay(Graphics2D graphics, boolean displaySourcesNames, Map<Integer,Set<Integer>> occupied) {
			OverlayStyle os;

			if (ssb.selectedSources.contains(sac)) {
				os = styles.get("SELECTED");
			}
			else {
				os = styles.get("DEFAULT");
			}
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
						graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
							RenderingHints.VALUE_ANTIALIAS_ON);

						graphics.setPaint(os.getIntersectionFillColor());
						graphics.fill(intersection);

						graphics.setPaint(os.getIntersectionColor());
						graphics.setStroke(os.getIntersectionStroke());
						graphics.draw(intersection);

						if (displaySourcesNames) {
							Area a = new Area(intersection);
							a.intersect(new Area(screen));
							double cx = a.getBounds2D().getCenterX();
							double cy = a.getBounds2D().getCenterY();
							//graphics.drawString(sac.getSpimSource().getName(),(int) cx,(int)cy);
							graphics.setColor(os.getFrontColor());
							occupied = displayAt(graphics, cx, cy, sac.getSpimSource().getName(), occupied);
						}
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
		SourceSelectorOverlay.OverlayStyle
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

	public static class SelectedOverlayStyle implements
		SourceSelectorOverlay.OverlayStyle
	{

		final Color backColor = new Color(0xF7BF18);

		final Color frontColor = new Color(0xC7F718);

		final Color intersectionFillColor = new Color(0x30B66A00, true);

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

	/**
	 * Drag Selection Behaviour
	 */
	class RectangleSelectSourcesBehaviour implements DragBehaviour {

		final String mode;

		public RectangleSelectSourcesBehaviour(String mode) {
			this.mode = mode;
		}

		@Override
		public void init(int x, int y) {
			startCurrentSelection(x, y);
			viewer.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
			switch (mode) {
				case SourceSelectorBehaviour.SET:
					viewer.showMessage("Set Selection");
					break;
				case SourceSelectorBehaviour.ADD:
					viewer.showMessage("Add Selection");
					break;
				case SourceSelectorBehaviour.REMOVE:
					viewer.showMessage("Remove Selection");
					break;
			}
		}

		@Override
		public void drag(int x, int y) {
			updateCurrentSelection(x, y);
			viewer.getDisplay().repaint();
			// viewer.paint(); // TODO : understand how to remove it from here ?
		}

		@Override
		public void end(int x, int y) {
			endCurrentSelection(x, y, mode);
			viewer.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
		}
	}

}
