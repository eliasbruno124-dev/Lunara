export type VaultWriteGuard = () => void;

/** Serializes the local vault's destructive boundary against encrypted writes. */
export class LocalVaultCoordinator {
  private generation = 0;
  private activeWrite: Promise<unknown> | null = null;
  private deletion: Promise<void> | null = null;

  get busy(): boolean {
    return this.activeWrite !== null || this.deletion !== null;
  }

  get deleting(): boolean {
    return this.deletion !== null;
  }

  invalidate(): number {
    this.generation += 1;
    return this.generation;
  }

  capture(): number {
    return this.generation;
  }

  isCurrent(generation: number): boolean {
    return generation === this.generation && this.deletion === null;
  }

  async write<T>(operation: (assertCurrent: VaultWriteGuard) => Promise<T>): Promise<T> {
    if (this.activeWrite || this.deletion) throw new Error('Local vault is busy');
    const generation = this.generation;
    const assertCurrent = (): void => {
      if (!this.isCurrent(generation)) throw new Error('Local vault write was invalidated');
    };
    const running = (async () => {
      assertCurrent();
      const result = await operation(assertCurrent);
      assertCurrent();
      return result;
    })();
    this.activeWrite = running;
    try {
      return await running;
    } finally {
      if (this.activeWrite === running) this.activeWrite = null;
    }
  }

  async delete(clearPersistedVault: () => Promise<void>, invalidateRuntime: () => void): Promise<void> {
    if (this.deletion) return this.deletion;
    this.invalidate();
    invalidateRuntime();
    const previousWrite = this.activeWrite;
    const deleting = (async () => {
      if (previousWrite) await previousWrite.catch(() => undefined);
      await clearPersistedVault();
    })();
    this.deletion = deleting;
    try {
      await deleting;
    } finally {
      if (this.deletion === deleting) this.deletion = null;
    }
  }
}
