import 'package:flutter/material.dart';
import '../config/theme.dart';

class PermissionDialog extends StatelessWidget {
  final VoidCallback onRequestPermission;
  final VoidCallback onOpenSettings;
  final bool isPermanentlyDenied;

  const PermissionDialog({
    super.key,
    required this.onRequestPermission,
    required this.onOpenSettings,
    this.isPermanentlyDenied = false,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Icon Container
            Container(
              padding: const EdgeInsets.all(32),
              decoration: BoxDecoration(
                gradient: AppColors.primaryGradient,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primaryStart.withValues(alpha: 0.4),
                    blurRadius: 30,
                    offset: const Offset(0, 10),
                  ),
                ],
              ),
              child: const Icon(
                Icons.folder_open_rounded,
                size: 64,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 32),

            // Title
            Text(
              'Storage Permission Required',
              style: TextStyle(
                color: AppColors.textPrimary,
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),

            // Description
            Text(
              isPermanentlyDenied
                  ? 'Storage permission was denied. Please enable it in app settings to view WhatsApp statuses.'
                  : 'Status Saver needs storage permission to access and save WhatsApp statuses.',
              style: TextStyle(
                color: AppColors.textSecondary,
                fontSize: 14,
                height: 1.5,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 40),

            // Action Button
            SizedBox(
              width: double.infinity,
              height: 56,
              child: ElevatedButton(
                onPressed: isPermanentlyDenied ? onOpenSettings : onRequestPermission,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  elevation: 0,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      isPermanentlyDenied ? Icons.settings_rounded : Icons.check_circle_rounded,
                      size: 20,
                    ),
                    const SizedBox(width: 12),
                    Text(
                      isPermanentlyDenied ? 'Open Settings' : 'Grant Permission',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ),

            if (!isPermanentlyDenied) ...[
              const SizedBox(height: 16),
              TextButton(
                onPressed: () {
                  // Show info about why permission is needed
                  showDialog(
                    context: context,
                    builder: (context) => AlertDialog(
                      backgroundColor: AppColors.surfaceDark,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(20),
                      ),
                      title: Text(
                        'Why do we need this?',
                        style: TextStyle(color: AppColors.textPrimary),
                      ),
                      content: Text(
                        'Status Saver requires storage access to:\n\n'
                        '• Find WhatsApp status files on your device\n'
                        '• Save statuses to your gallery\n'
                        '• Cache status files for offline viewing\n\n'
                        'Your files are never uploaded or shared.',
                        style: TextStyle(
                          color: AppColors.textSecondary,
                          height: 1.5,
                        ),
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context),
                          child: Text(
                            'Got it',
                            style: TextStyle(color: AppColors.accent),
                          ),
                        ),
                      ],
                    ),
                  );
                },
                child: Text(
                  'Why is this needed?',
                  style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 14,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class NoWhatsAppDialog extends StatelessWidget {
  const NoWhatsAppDialog({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Icon Container
            Container(
              padding: const EdgeInsets.all(32),
              decoration: BoxDecoration(
                color: AppColors.warning.withValues(alpha: 0.2),
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.warning_amber_rounded,
                size: 64,
                color: AppColors.warning,
              ),
            ),
            const SizedBox(height: 32),

            // Title
            Text(
              'WhatsApp Not Found',
              style: TextStyle(
                color: AppColors.textPrimary,
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),

            // Description
            Text(
              'We couldn\'t find WhatsApp status folder on your device. '
              'Make sure WhatsApp is installed and you have viewed some statuses.',
              style: TextStyle(
                color: AppColors.textSecondary,
                fontSize: 14,
                height: 1.5,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),

            // Tips
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.surfaceLight.withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Tips:',
                    style: TextStyle(
                      color: AppColors.accent,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  _buildTip('Install WhatsApp if not already installed'),
                  _buildTip('Open WhatsApp and view some statuses'),
                  _buildTip('Come back here and refresh'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTip(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(
            Icons.check_circle_outline_rounded,
            size: 16,
            color: AppColors.textSecondary,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                color: AppColors.textSecondary,
                fontSize: 12,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
