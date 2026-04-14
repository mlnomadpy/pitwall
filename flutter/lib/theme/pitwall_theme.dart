import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Pitwall design system — dark, premium, track-focused.
/// Inspired by motorsport timing screens and professional data logging tools.
class PitwallColors {
  // Brand
  static const background = Color(0xFF0A0A0F);
  static const surface = Color(0xFF13131A);
  static const surfaceElevated = Color(0xFF1C1C26);
  static const border = Color(0xFF2A2A38);

  // Grip signal — green is safe, red is over-limit
  static const gripGreen = Color(0xFF00E676);
  static const gripGreenDim = Color(0xFF004D27);
  static const gripRed = Color(0xFFFF1744);
  static const gripRedDim = Color(0xFF4D0012);
  static const gripYellow = Color(0xFFFFD600);

  // Data colours
  static const speedBlue = Color(0xFF448AFF);
  static const brakeRed = Color(0xFFFF5252);
  static const throttleGreen = Color(0xFF69F0AE);
  static const gForceOrange = Color(0xFFFF6D00);
  static const goldStandard = Color(0xFFFFD740);

  // Text
  static const textPrimary = Color(0xFFF0F0F8);
  static const textSecondary = Color(0xFF9090A8);
  static const textDim = Color(0xFF4A4A60);
}

class PitwallTheme {
  static ThemeData get dark => ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: PitwallColors.background,
    colorScheme: const ColorScheme.dark(
      primary: PitwallColors.gripGreen,
      secondary: PitwallColors.speedBlue,
      surface: PitwallColors.surface,
      error: PitwallColors.gripRed,
    ),
    textTheme: GoogleFonts.interTextTheme(ThemeData.dark().textTheme).copyWith(
      displayLarge: GoogleFonts.inter(
        color: PitwallColors.textPrimary,
        fontSize: 96,
        fontWeight: FontWeight.w800,
        letterSpacing: -4,
      ),
      headlineMedium: GoogleFonts.inter(
        color: PitwallColors.textPrimary,
        fontSize: 28,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.5,
      ),
      bodyLarge: GoogleFonts.inter(
        color: PitwallColors.textSecondary,
        fontSize: 14,
        fontWeight: FontWeight.w400,
      ),
      labelSmall: GoogleFonts.robotoMono(
        color: PitwallColors.textDim,
        fontSize: 11,
        letterSpacing: 1.2,
      ),
    ),
    dividerColor: PitwallColors.border,
    cardTheme: const CardThemeData(
      color: PitwallColors.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(12)),
        side: BorderSide(color: PitwallColors.border, width: 1),
      ),
    ),
  );
}
