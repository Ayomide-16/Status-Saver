import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppColors {
  // Primary Gradient Colors
  static const Color primaryStart = Color(0xFF667eea);
  static const Color primaryEnd = Color(0xFF764ba2);
  
  // Accent Colors
  static const Color accent = Color(0xFF00d4aa);
  static const Color accentLight = Color(0xFF00f5c4);
  
  // Background Colors
  static const Color backgroundDark = Color(0xFF0a0a0a);
  static const Color backgroundLight = Color(0xFF1a1a2e);
  static const Color surfaceDark = Color(0xFF16213e);
  static const Color surfaceLight = Color(0xFF1f2937);
  
  // Card Colors (Glassmorphism)
  static Color cardBackground = Colors.white.withValues(alpha: 0.1);
  static Color cardBorder = Colors.white.withValues(alpha: 0.2);
  
  // Text Colors
  static const Color textPrimary = Colors.white;
  static Color textSecondary = Colors.white.withValues(alpha: 0.7);
  static Color textTertiary = Colors.white.withValues(alpha: 0.5);
  
  // Status Colors
  static const Color success = Color(0xFF00d4aa);
  static const Color error = Color(0xFFff4757);
  static const Color warning = Color(0xFFffa502);
  static const Color info = Color(0xFF3498db);
  
  // Tab Colors
  static const Color imageTabColor = Color(0xFF00d4aa);
  static const Color videoTabColor = Color(0xFFff6b6b);
  
  // Gradients
  static const LinearGradient primaryGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [primaryStart, primaryEnd],
  );
  
  static const LinearGradient backgroundGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [backgroundLight, backgroundDark],
  );
  
  static const LinearGradient accentGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [accent, accentLight],
  );
}

class AppTheme {
  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: AppColors.backgroundDark,
      primaryColor: AppColors.primaryStart,
      colorScheme: ColorScheme.dark(
        primary: AppColors.primaryStart,
        secondary: AppColors.accent,
        surface: AppColors.surfaceDark,
        error: AppColors.error,
      ),
      textTheme: GoogleFonts.poppinsTextTheme(
        ThemeData.dark().textTheme,
      ).apply(
        bodyColor: AppColors.textPrimary,
        displayColor: AppColors.textPrimary,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: GoogleFonts.poppins(
          color: AppColors.textPrimary,
          fontSize: 20,
          fontWeight: FontWeight.w600,
        ),
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: AppColors.surfaceDark.withValues(alpha: 0.9),
        selectedItemColor: AppColors.accent,
        unselectedItemColor: AppColors.textSecondary,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      tabBarTheme: TabBarThemeData(
        labelColor: AppColors.accent,
        unselectedLabelColor: AppColors.textSecondary,
        indicatorSize: TabBarIndicatorSize.label,
        dividerColor: Colors.transparent,
        labelStyle: GoogleFonts.poppins(
          fontWeight: FontWeight.w600,
          fontSize: 14,
        ),
        unselectedLabelStyle: GoogleFonts.poppins(
          fontWeight: FontWeight.w500,
          fontSize: 14,
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: AppColors.accent,
        foregroundColor: AppColors.backgroundDark,
        elevation: 8,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
      ),
      cardTheme: CardThemeData(
        color: AppColors.cardBackground,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: AppColors.cardBorder),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.surfaceLight,
        contentTextStyle: GoogleFonts.poppins(color: AppColors.textPrimary),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        behavior: SnackBarBehavior.floating,
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: AppColors.surfaceDark,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
        titleTextStyle: GoogleFonts.poppins(
          color: AppColors.textPrimary,
          fontSize: 20,
          fontWeight: FontWeight.w600,
        ),
        contentTextStyle: GoogleFonts.poppins(
          color: AppColors.textSecondary,
          fontSize: 14,
        ),
      ),
    );
  }
}

// Custom Decorations
class AppDecorations {
  static BoxDecoration get glassCard => BoxDecoration(
    color: AppColors.cardBackground,
    borderRadius: BorderRadius.circular(16),
    border: Border.all(color: AppColors.cardBorder),
    boxShadow: [
      BoxShadow(
        color: Colors.black.withValues(alpha: 0.2),
        blurRadius: 20,
        offset: const Offset(0, 10),
      ),
    ],
  );
  
  static BoxDecoration get gradientCard => BoxDecoration(
    gradient: AppColors.primaryGradient,
    borderRadius: BorderRadius.circular(16),
    boxShadow: [
      BoxShadow(
        color: AppColors.primaryStart.withValues(alpha: 0.4),
        blurRadius: 20,
        offset: const Offset(0, 10),
      ),
    ],
  );

  static BoxDecoration get accentCard => BoxDecoration(
    gradient: AppColors.accentGradient,
    borderRadius: BorderRadius.circular(16),
    boxShadow: [
      BoxShadow(
        color: AppColors.accent.withValues(alpha: 0.4),
        blurRadius: 20,
        offset: const Offset(0, 10),
      ),
    ],
  );
}
