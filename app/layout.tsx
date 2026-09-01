import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PocketShell — Downloads",
  description:
    "A real Linux terminal for Android. Alpine + proot + apk — nothing faked.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className="dark">
      <body className="bg-neutral-950 text-neutral-100 antialiased">
        {children}
      </body>
    </html>
  );
}
