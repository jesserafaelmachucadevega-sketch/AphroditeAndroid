package com.aphrodite.core.adaptive

import com.aphrodite.core.common.layout.LayoutMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceProfileResolutionTest {

    private fun facts(
        widthDp: Int = 411,
        heightDp: Int = 914,
        smallestWidthDp: Int = 411,
        hasFoldingFeature: Boolean = false,
        isSeparating: Boolean = false,
        posture: FoldPosture = FoldPosture.Flat,
    ) = WindowFacts(
        widthDp = widthDp,
        heightDp = heightDp,
        smallestWidthDp = smallestWidthDp,
        hasFoldingFeature = hasFoldingFeature,
        isSeparating = isSeparating,
        posture = posture,
    )

    // ── Explicit modes always win ─────────────────────────────────────────────

    @Test
    fun `explicit phone mode ignores tablet sized windows`() {
        val profile = resolveDeviceProfile(
            LayoutMode.Phone,
            facts(widthDp = 1280, smallestWidthDp = 900),
        )
        assertEquals(DeviceProfile.Phone, profile)
    }

    @Test
    fun `explicit tablet mode promotes a phone sized window`() {
        val profile = resolveDeviceProfile(LayoutMode.Tablet, facts())
        assertEquals(DeviceProfile.Tablet, profile)
    }

    @Test
    fun `explicit foldable mode applies without a folding feature`() {
        val profile = resolveDeviceProfile(LayoutMode.Foldable, facts())
        assertEquals(DeviceProfile.Fold, profile)
    }

    @Test
    fun `explicit desktop mode applies to a phone sized window`() {
        val profile = resolveDeviceProfile(LayoutMode.Desktop, facts())
        assertEquals(DeviceProfile.Desktop, profile)
    }

    // ── Automatic detection ───────────────────────────────────────────────────

    @Test
    fun `a typical phone resolves to phone`() {
        val profile = resolveDeviceProfile(LayoutMode.Automatic, facts())
        assertEquals(DeviceProfile.Phone, profile)
    }

    @Test
    fun `a phone in landscape is still a phone`() {
        // 892x411 is wider than the tablet breakpoint only if you use width alone, which is
        // exactly the mistake this guards against: smallest width stays small.
        val profile = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(widthDp = 892, heightDp = 411, smallestWidthDp = 411),
        )
        assertEquals(DeviceProfile.Phone, profile)
    }

    @Test
    fun `the tablet breakpoint resolves to tablet`() {
        val profile = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(widthDp = 800, heightDp = 1280, smallestWidthDp = 800),
        )
        assertEquals(DeviceProfile.Tablet, profile)
    }

    @Test
    fun `a ten inch tablet in landscape resolves to tablet`() {
        // Roughly a 10.9" 2304x1440 panel at 2x density, landscape.
        val profile = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(widthDp = 1152, heightDp = 720, smallestWidthDp = 720),
        )
        assertEquals(DeviceProfile.Tablet, profile)
    }

    @Test
    fun `a wide desktop sized window resolves to desktop`() {
        val profile = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(widthDp = 1440, heightDp = 900, smallestWidthDp = 900),
        )
        assertEquals(DeviceProfile.Desktop, profile)
    }

    // ── Foldables ─────────────────────────────────────────────────────────────

    @Test
    fun `a separating hinge resolves to fold`() {
        val profile = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(
                widthDp = 700,
                heightDp = 800,
                smallestWidthDp = 700,
                hasFoldingFeature = true,
                isSeparating = true,
                posture = FoldPosture.Book,
            ),
        )
        assertEquals(DeviceProfile.Fold, profile)
    }

    @Test
    fun `a non separating fold keeps the size based profile`() {
        // A flat foldable reports a folding feature that does not separate the display; the
        // layout should follow its width instead.
        val profile = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(
                widthDp = 800,
                heightDp = 1280,
                smallestWidthDp = 800,
                hasFoldingFeature = true,
                isSeparating = false,
            ),
        )
        assertEquals(DeviceProfile.Tablet, profile)
    }

    @Test
    fun `a folded phone reports phone until it is opened`() {
        val folded = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(
                widthDp = 360,
                heightDp = 800,
                smallestWidthDp = 360,
                hasFoldingFeature = true,
                isSeparating = false,
            ),
        )
        assertEquals(DeviceProfile.Phone, folded)

        val unfolded = resolveDeviceProfile(
            LayoutMode.Automatic,
            facts(
                widthDp = 700,
                heightDp = 800,
                smallestWidthDp = 700,
                hasFoldingFeature = true,
                isSeparating = true,
                posture = FoldPosture.Flat,
            ),
        )
        assertEquals(DeviceProfile.Fold, unfolded)
    }

    // ── Pane capabilities ─────────────────────────────────────────────────────

    @Test
    fun `only phone profiles are single pane`() {
        assertEquals(false, DeviceProfile.Phone.supportsTwoPanes)
        assertEquals(false, DeviceProfile.Phone.hasRail)
        assertEquals(true, DeviceProfile.Tablet.supportsTwoPanes)
        assertEquals(true, DeviceProfile.Fold.supportsTwoPanes)
        assertEquals(true, DeviceProfile.Desktop.supportsTwoPanes)
    }
}
