import 'package:flutter/material.dart';
import '../config/theme.dart';

class PermissionDialog extends StatelessWidget {
  final VoidCallback onRequestPermission;
  final VoidCallback onOpenSettings;

  const PermissionDialog({
    super.key,
    required this.onRequestPermission,
    required this.onOpenSettings,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.folder_off_rounded,
              size: 80,
              color: isDark 
                  ? AppColors.darkTextSecondary 
                  : AppColors.lightTextSecondary,
            ),
            const SizedBox(height: 24),
            Text(
              'Storage Permission Required',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: isDark 
                    ? AppColors.darkTextPrimary 
                    : AppColors.lightTextPrimary,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              'Status Saver needs storage access to find and save WhatsApp statuses.',
              style: TextStyle(
                fontSize: 14,
                color: isDark 
                    ? AppColors.darkTextSecondary 
                    : AppColors.lightTextSecondary,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 32),
            ElevatedButton.icon(
              onPressed: onRequestPermission,
              icon: const Icon(Icons.check_rounded),
              label: const Text('Grant Permission'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: onOpenSettings,
              icon: const Icon(Icons.settings_rounded),
              label: const Text('Open Settings'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Dialog shown when SAF folder access is needed (Android 11+)
class SafAccessDialog extends StatelessWidget {
  final VoidCallback onRequestAccess;

  const SafAccessDialog({
    super.key,
    required this.onRequestAccess,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 100,
              height: 100,
              decoration: BoxDecoration(
                color: AppColors.primaryGreen.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.folder_open_rounded,
                size: 50,
                color: AppColors.primaryGreen,
              ),
            ),
            const SizedBox(height: 24),
            Text(
              'Select WhatsApp Status Folder',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: isDark 
                    ? AppColors.darkTextPrimary 
                    : AppColors.lightTextPrimary,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              'Android requires you to manually select the WhatsApp status folder.',
              style: TextStyle(
                fontSize: 14,
                color: isDark 
                    ? AppColors.darkTextSecondary 
                    : AppColors.lightTextSecondary,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: isDark ? AppColors.darkSurface : AppColors.lightSurface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Navigate to:',
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: isDark 
                          ? AppColors.darkTextPrimary 
                          : AppColors.lightTextPrimary,
                    ),
                  ),
                  const SizedBox(height: 8),
                  _buildPathStep(context, '1', 'Android'),
                  _buildPathStep(context, '2', 'media'),
                  _buildPathStep(context, '3', 'com.whatsapp'),
                  _buildPathStep(context, '4', 'WhatsApp'),
                  _buildPathStep(context, '5', 'Media'),
                  _buildPathStep(context, '6', '.Statuses', isLast: true),
                ],
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: onRequestAccess,
              icon: const Icon(Icons.folder_open_rounded),
              label: const Text('Select Folder'),
            ),
            const SizedBox(height: 8),
            Text(
              'Then tap "Use this folder"',
              style: TextStyle(
                fontSize: 12,
                color: isDark 
                    ? AppColors.darkTextSecondary 
                    : AppColors.lightTextSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPathStep(BuildContext context, String number, String folder, {bool isLast = false}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Padding(
      padding: const EdgeInsets.only(left: 8, top: 4),
      child: Row(
        children: [
          Container(
            width: 20,
            height: 20,
            decoration: BoxDecoration(
              color: AppColors.primaryGreen,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                number,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          Icon(
            Icons.folder_rounded,
            size: 16,
            color: AppColors.primaryGreen,
          ),
          const SizedBox(width: 4),
          Text(
            folder,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isLast ? FontWeight.bold : FontWeight.normal,
              color: isLast 
                  ? AppColors.primaryGreen
                  : (isDark 
                      ? AppColors.darkTextSecondary 
                      : AppColors.lightTextSecondary),
            ),
          ),
        ],
      ),
    );
  }
}

class NoWhatsAppDialog extends StatelessWidget {
  const NoWhatsAppDialog({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.warning_amber_rounded,
              size: 80,
              color: AppColors.warning,
            ),
            const SizedBox(height: 24),
            Text(
              'WhatsApp Not Found',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: isDark 
                    ? AppColors.darkTextPrimary 
                    : AppColors.lightTextPrimary,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              'WhatsApp status folder was not found on this device. Make sure WhatsApp is installed and you have viewed some statuses.',
              style: TextStyle(
                fontSize: 14,
                color: isDark 
                    ? AppColors.darkTextSecondary 
                    : AppColors.lightTextSecondary,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
