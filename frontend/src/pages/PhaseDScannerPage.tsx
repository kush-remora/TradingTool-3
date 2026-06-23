import React, { useState, useEffect } from 'react';
import { Upload, FileText, AlertCircle, RefreshCw } from 'lucide-react';

interface PhaseCWatchlistDto {
  symbol: string;
  marketcapname: string | null;
  closePrice: number | null;
  pctChange: string | null;
  volume: number | null;
  sector: string | null;
  industry: string | null;
  roce: number | null;
  ronw: number | null;
  netProfit3qAgo: number | null;
  debtEquity: number | null;
  volDry200Min: number | null;
  volDry60Min: number | null;
  volDry200Min105: number | null;
  volDry60Min105: number | null;
  atrCount: number | null;
}

interface PhaseCWatchlistRow extends PhaseCWatchlistDto {
  addedOn: string;
  lastSeenOn: string;
  status: string;
  instrumentToken: number | null;
}

export function PhaseDScannerPage() {
  const [watchlist, setWatchlist] = useState<PhaseCWatchlistRow[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [uploading, setUploading] = useState<boolean>(false);
  const [uploadResult, setUploadResult] = useState<{inserted: number, updated: number} | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchWatchlist = async () => {
    try {
      setLoading(true);
      const res = await fetch('/api/strategy/phase-c/dashboard');
      if (!res.ok) throw new Error('Failed to fetch watchlist');
      const data = await res.json();
      setWatchlist(data);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWatchlist();
  }, []);

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    setError(null);
    setUploadResult(null);

    const reader = new FileReader();
    reader.onload = async (event) => {
      try {
        const text = event.target?.result as string;
        const parsedRows = parseCsvOrTsv(text);
        
        if (parsedRows.length === 0) {
            throw new Error("No valid rows found in the file.");
        }

        const res = await fetch('/api/strategy/phase-c/upload', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ rows: parsedRows }),
        });

        if (!res.ok) {
            const errRes = await res.json();
            throw new Error(errRes.error || 'Failed to upload rows');
        }

        const data = await res.json();
        setUploadResult({ inserted: data.insertedCount, updated: data.updatedCount });
        fetchWatchlist(); // refresh table
      } catch (err: any) {
        setError(err.message);
      } finally {
        setUploading(false);
      }
    };
    reader.onerror = () => {
      setError('Error reading file');
      setUploading(false);
    };
    reader.readAsText(file);
    // clear input
    e.target.value = '';
  };

  const parseCsvOrTsv = (text: string): PhaseCWatchlistDto[] => {
    const lines = text.split('\n').map(line => line.trim()).filter(line => line.length > 0);
    if (lines.length < 2) return [];

    // Detect separator (tab vs comma)
    const isTsv = lines[0].includes('\t');
    const sep = isTsv ? '\t' : ',';
    
    // Extract headers and lowercase them for flexible matching
    const headers = lines[0].split(sep).map(h => h.trim().toLowerCase());
    
    const parseNumber = (val: string | undefined) => {
        if (!val) return null;
        const cleaned = val.replace(/,/g, '').trim();
        const num = parseFloat(cleaned);
        return isNaN(num) ? null : num;
    };

    const parseSymbol = (val: string | undefined) => {
      if (!val) return null;
      // Chartink symbols might have HTML if we pasted rich text, but usually plain string
      return val.trim();
    };

    const rows: PhaseCWatchlistDto[] = [];

    for (let i = 1; i < lines.length; i++) {
        const cols = lines[i].split(sep);
        
        const getCol = (nameKeys: string[]) => {
            const idx = headers.findIndex(h => nameKeys.some(k => h.includes(k)));
            return idx !== -1 ? cols[idx] : undefined;
        };

        const symbol = parseSymbol(getCol(['symbol']));
        if (!symbol) continue;

        rows.push({
            symbol: symbol,
            marketcapname: getCol(['marketcap', 'market cap'])?.trim() || null,
            closePrice: parseNumber(getCol(['close'])),
            pctChange: getCol(['change', '% change'])?.trim() || null,
            volume: parseNumber(getCol(['volume'])),
            sector: getCol(['sector'])?.trim() || null,
            industry: getCol(['industry'])?.trim() || null,
            roce: parseNumber(getCol(['roce', 'return on capital employed'])),
            ronw: parseNumber(getCol(['ronw', 'return on net worth'])),
            netProfit3qAgo: parseNumber(getCol(['net profit', '3 quarter ago'])),
            debtEquity: parseNumber(getCol(['debt'])),
            volDry200Min: parseNumber(getCol(['volume dry on 200 days min'])),
            volDry60Min: parseNumber(getCol(['volume dry on 60 days min'])),
            volDry200Min105: parseNumber(getCol(['volume dry on 200 days min * 1.05'])),
            volDry60Min105: parseNumber(getCol(['volume dry on 60days min * 1.05'])),
            atrCount: parseNumber(getCol(['atr'])),
        });
    }

    return rows;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-[var(--color-text-primary)]">Phase D Ignition Scanner</h2>
          <p className="text-sm text-[var(--color-text-tertiary)] mt-1">Upload Chartink Phase C (Dry-Up) Watchlist to trigger delivery ignition scans.</p>
        </div>
      </div>

      <div className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-lg p-6">
        <h3 className="text-sm font-medium text-[var(--color-text-secondary)] mb-4">Upload Chartink CSV/TSV</h3>
        
        <div className="flex flex-col items-center justify-center p-8 border-2 border-dashed border-[var(--color-border-subtle)] rounded-lg hover:bg-[var(--color-surface-hover)] transition-colors relative">
            <input 
                type="file" 
                accept=".csv, .tsv, text/plain" 
                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                onChange={handleFileUpload}
                disabled={uploading}
            />
            <div className="flex flex-col items-center pointer-events-none">
                {uploading ? (
                    <RefreshCw className="w-8 h-8 text-[var(--color-text-tertiary)] animate-spin mb-3" />
                ) : (
                    <Upload className="w-8 h-8 text-[var(--color-text-tertiary)] mb-3" />
                )}
                <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                    {uploading ? 'Processing File...' : 'Click or drag file to upload'}
                </span>
                <span className="text-xs text-[var(--color-text-tertiary)] mt-1">Supports CSV or TSV exports from Chartink</span>
            </div>
        </div>

        {error && (
            <div className="mt-4 flex items-center gap-2 p-3 bg-red-500/10 text-red-500 rounded-md text-sm border border-red-500/20">
                <AlertCircle className="w-4 h-4" />
                {error}
            </div>
        )}

        {uploadResult && (
            <div className="mt-4 flex items-center gap-2 p-3 bg-green-500/10 text-green-500 rounded-md text-sm border border-green-500/20">
                <FileText className="w-4 h-4" />
                Successfully imported/updated {uploadResult.inserted} stocks.
            </div>
        )}
      </div>

      <div className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-lg overflow-hidden flex flex-col">
          <div className="p-4 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
            <h3 className="text-sm font-medium text-[var(--color-text-secondary)]">Phase C Watchlist</h3>
            <div className="text-xs text-[var(--color-text-tertiary)] bg-[var(--color-surface-hover)] px-2 py-1 rounded">
                {watchlist.length} Total Candidates
            </div>
          </div>
          
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm whitespace-nowrap">
                <thead className="bg-[var(--color-surface-hover)] text-[var(--color-text-tertiary)] text-xs uppercase font-medium">
                    <tr>
                        <th className="px-4 py-3">Symbol</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3">Added On</th>
                        <th className="px-4 py-3">Last Seen</th>
                        <th className="px-4 py-3 text-right">Close</th>
                        <th className="px-4 py-3 text-right">Vol</th>
                        <th className="px-4 py-3">Sector</th>
                        <th className="px-4 py-3 text-right">Vol Dry (200d)</th>
                        <th className="px-4 py-3 text-right">Vol Dry (60d)</th>
                        <th className="px-4 py-3 text-right">Vol Dry (200d * 1.05)</th>
                        <th className="px-4 py-3 text-right">Vol Dry (60d * 1.05)</th>
                        <th className="px-4 py-3 text-right">ATR&lt;2%</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-[var(--color-border-subtle)]">
                    {loading ? (
                        <tr>
                            <td colSpan={12} className="px-4 py-8 text-center text-[var(--color-text-tertiary)]">
                                <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2" />
                                Loading Watchlist...
                            </td>
                        </tr>
                    ) : watchlist.length === 0 ? (
                        <tr>
                            <td colSpan={12} className="px-4 py-8 text-center text-[var(--color-text-tertiary)]">
                                No candidates in Phase C Watchlist. Upload a CSV to begin.
                            </td>
                        </tr>
                    ) : (
                        watchlist.map(row => (
                            <tr key={row.symbol} className="hover:bg-[var(--color-surface-hover)] transition-colors">
                                <td className="px-4 py-3 font-medium text-[var(--color-text-primary)]">{row.symbol}</td>
                                <td className="px-4 py-3">
                                    <span className="px-2 py-1 bg-blue-500/10 text-blue-500 text-[10px] uppercase font-bold rounded">
                                        {row.status}
                                    </span>
                                </td>
                                <td className="px-4 py-3 text-[var(--color-text-tertiary)]">{row.addedOn}</td>
                                <td className="px-4 py-3 text-[var(--color-text-tertiary)]">{row.lastSeenOn}</td>
                                <td className="px-4 py-3 text-right text-[var(--color-text-secondary)]">{row.closePrice?.toFixed(2) || '-'}</td>
                                <td className="px-4 py-3 text-right text-[var(--color-text-secondary)]">{row.volume?.toLocaleString() || '-'}</td>
                                <td className="px-4 py-3 text-[var(--color-text-secondary)] truncate max-w-[150px]">{row.sector || '-'}</td>
                                <td className="px-4 py-3 text-right text-[var(--color-text-secondary)] font-mono">{row.volDry200Min !== null ? row.volDry200Min : '-'}</td>
                                <td className="px-4 py-3 text-right text-[var(--color-text-secondary)] font-mono">{row.volDry60Min !== null ? row.volDry60Min : '-'}</td>
                                <td className="px-4 py-3 text-right text-[var(--color-text-secondary)] font-mono">{row.volDry200Min105 !== null ? row.volDry200Min105 : '-'}</td>
                                <td className="px-4 py-3 text-right text-[var(--color-text-secondary)] font-mono">{row.volDry60Min105 !== null ? row.volDry60Min105 : '-'}</td>
                                <td className="px-4 py-3 text-right text-[var(--color-text-secondary)] font-mono">{row.atrCount !== null ? row.atrCount : '-'}</td>
                            </tr>
                        ))
                    )}
                </tbody>
            </table>
          </div>
      </div>
    </div>
  );
}
