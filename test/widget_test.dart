// Basic widget test for Status Saver app

import 'package:flutter_test/flutter_test.dart';
import 'package:status_saver/main.dart';

void main() {
  testWidgets('App launches successfully', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const StatusSaverApp());

    // Verify that splash screen elements are present
    expect(find.text('Status Saver'), findsOneWidget);
  });
}
