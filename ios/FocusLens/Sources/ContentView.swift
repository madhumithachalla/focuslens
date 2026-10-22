import SwiftUI
import WebKit
import UIKit

/// The FocusLens website, shown in a native window. Links to other sites open in Safari.
struct ContentView: View {
    static let startURL = URL(string: "https://madhumithachalla.github.io/focuslens/")!

    var body: some View {
        WebView(url: Self.startURL)
    }
}

struct WebView: UIViewRepresentable {
    let url: URL

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()          // keeps sign-in between launches
        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator
        view.allowsBackForwardNavigationGestures = true
        view.load(URLRequest(url: url))
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        // Hosts that must stay inside the app: FocusLens itself and Firebase sign-in.
        private let inAppHosts = [
            "madhumithachalla.github.io",
            "focuslens-d5379.firebaseapp.com",
            "accounts.google.com",
            "apis.google.com",
        ]

        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction,
                     decidePolicy decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url,
                  let host = url.host,
                  navigationAction.navigationType == .linkActivated || navigationAction.navigationType == .other,
                  navigationAction.targetFrame?.isMainFrame ?? true
            else {
                decisionHandler(.allow)
                return
            }
            let stayInside = inAppHosts.contains { host == $0 || host.hasSuffix("." + $0) }
            if stayInside || url.scheme == "about" {
                decisionHandler(.allow)
            } else {
                UIApplication.shared.open(url)   // other sites open in Safari
                decisionHandler(.cancel)
            }
        }
    }
}
