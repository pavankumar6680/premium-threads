export function Footer() {
  return (
    <footer className="border-t border-slate-200 bg-white">
      <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-6 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between">
        <p>© {new Date().getFullYear()} BuildMyBundle. A portfolio project.</p>
        <p>Payments are simulated. No real charges are made.</p>
      </div>
    </footer>
  );
}
