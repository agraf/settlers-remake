/*******************************************************************************
 * Copyright (c) 2026
 *
 * Render-resolution divisor: divides the framebuffer size that the swing
 * graphics layer asks the backend to allocate, so the backend renders at
 * a coarser resolution than the on-screen surface and the platform compositor
 * (CoreAnimation on macOS, Wayland/X compositors on Linux, DWM on Windows)
 * scales the swapchain image up at present time.
 *
 * Useful on HiDPI / Retina displays where the game's pixel-perfect UI
 * elements look uncomfortably small at native pixel density. With
 * {@code -Dgo.graphics.render-divisor=2.0} a Retina backing store of
 * 2560x1600 ends up rendering at 1280x800, then the compositor bilinearly
 * upscales to the visible 2560x1600, doubling the apparent size of every
 * fixed-pixel element.
 *
 * Default ({@code auto} or unset): query the default-screen AWT scale
 * transform and pick 2.0 on HiDPI screens, 1.0 otherwise. Explicit values
 * such as {@code 1.0}, {@code 1.5}, {@code 2.0} ... bypass auto-detection.
 *
 * The same divisor must also be applied to mouse coordinates in
 * {@code GOSwingEventConverter} so that input lands on the same coordinate
 * grid that the renderer is drawing into.
 ******************************************************************************/
package go.graphics;

import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;

public final class RenderScale {

	/** System property name. Value is a positive double or "auto"; defaults to "auto". */
	public static final String DIVISOR_PROPERTY = "go.graphics.render-divisor";

	/**
	 * Auto-detected divisor based on the default screen's AWT scale. Computed once,
	 * lazily, on first access. {@link Double#NaN} until then.
	 */
	private static volatile double cachedAutoDivisor = Double.NaN;

	private RenderScale() {}

	/**
	 * @return The currently configured divisor (>= 1.0). Read fresh each call so it
	 *         can be tweaked via {@link System#setProperty(String, String)} at runtime
	 *         (e.g. from a settings menu); values < 1.0 are clamped to 1.0 and bad
	 *         strings fall back to auto-detect.
	 */
	public static double getDivisor() {
		String raw = System.getProperty(DIVISOR_PROPERTY);
		if (raw != null && !raw.isEmpty() && !"auto".equalsIgnoreCase(raw)) {
			try {
				double v = Double.parseDouble(raw);
				return v >= 1.0 ? v : 1.0;
			} catch (NumberFormatException e) {
				// fall through to auto-detect
			}
		}
		return autoDetect();
	}

	/**
	 * Auto-detect: 2.0 on HiDPI displays (default-screen AWT transform scale &gt; 1.0),
	 * 1.0 otherwise. Cached after the first successful read.
	 */
	private static double autoDetect() {
		double cached = cachedAutoDivisor;
		if (!Double.isNaN(cached)) return cached;

		double divisor = 1.0;
		double scale = 1.0;
		boolean detected = false;
		try {
			if (!GraphicsEnvironment.isHeadless()) {
				AffineTransform t = GraphicsEnvironment.getLocalGraphicsEnvironment()
						.getDefaultScreenDevice()
						.getDefaultConfiguration()
						.getDefaultTransform();
				if (t != null) {
					double sx = t.getScaleX();
					double sy = t.getScaleY();
					scale = Math.max(sx, sy);
					detected = true;
					// Anything materially above 1x counts as HiDPI; 1.05 leaves headroom for
					// the rounding errors that AppKit sometimes reports on integer-scaled
					// external monitors.
					if (scale > 1.05) {
						divisor = 2.0;
					}
				}
			}
		} catch (Throwable t) {
			// Defensive: any AWT failure (no display, broken init) -> divisor 1.0.
		}
		cachedAutoDivisor = divisor;
		if (detected) {
			System.out.println(String.format(
					"[RenderScale] auto-detected screen scale=%.2f, divisor=%.2f",
					scale, divisor));
		} else {
			System.out.println("[RenderScale] auto-detect unavailable (headless?), divisor=1.0");
		}
		return divisor;
	}

	/** Convenience: scaled framebuffer width given the on-screen pixel width. */
	public static int scaleDown(int onScreenPixels) {
		double v = onScreenPixels / getDivisor();
		int out = (int) Math.round(v);
		return Math.max(1, out);
	}
}
