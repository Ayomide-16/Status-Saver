import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/theme_provider.dart';
import 'home_screen.dart';
import 'saved_screen.dart';
import 'cache_screen.dart';

class MainNavigation extends StatefulWidget {
  const MainNavigation({super.key});

  @override
  State<MainNavigation> createState() => _MainNavigationState();
}

class _MainNavigationState extends State<MainNavigation> {
  int _currentIndex = 0;
  
  final List<Widget> _screens = const [
    HomeScreen(),
    SavedScreen(),
    CacheScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Scaffold(
      appBar: AppBar(
        title: Text(_getTitle()),
        actions: [
          // Theme Toggle Button
          IconButton(
            icon: Icon(
              isDark ? Icons.light_mode_rounded : Icons.dark_mode_rounded,
            ),
            onPressed: () {
              context.read<ThemeProvider>().toggleTheme();
            },
            tooltip: isDark ? 'Light Mode' : 'Dark Mode',
          ),
        ],
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() {
            _currentIndex = index;
          });
        },
        backgroundColor: isDark ? AppColors.darkSurface : AppColors.lightBackground,
        indicatorColor: AppColors.primaryGreen.withValues(alpha: 0.2),
        destinations: [
          NavigationDestination(
            icon: Icon(
              Icons.home_outlined,
              color: _currentIndex == 0 
                  ? AppColors.primaryGreen 
                  : (isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary),
            ),
            selectedIcon: const Icon(Icons.home_rounded, color: AppColors.primaryGreen),
            label: 'Status',
          ),
          NavigationDestination(
            icon: Icon(
              Icons.bookmark_outline_rounded,
              color: _currentIndex == 1 
                  ? AppColors.primaryGreen 
                  : (isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary),
            ),
            selectedIcon: const Icon(Icons.bookmark_rounded, color: AppColors.primaryGreen),
            label: 'Saved',
          ),
          NavigationDestination(
            icon: Icon(
              Icons.history_rounded,
              color: _currentIndex == 2 
                  ? AppColors.primaryGreen 
                  : (isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary),
            ),
            selectedIcon: const Icon(Icons.history_rounded, color: AppColors.primaryGreen),
            label: 'Recent',
          ),
        ],
      ),
    );
  }

  String _getTitle() {
    switch (_currentIndex) {
      case 0:
        return 'Status Saver';
      case 1:
        return 'Saved Status';
      case 2:
        return 'Recent Status';
      default:
        return 'Status Saver';
    }
  }
}
