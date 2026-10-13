package com.gitsimples

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

object GitOps {
    private val who = PersonIdent("Git Simples", "app@gitsimples.local")

    private fun open(dir: File): Git =
        if (File(dir, ".git").exists()) Git.open(dir)
        else Git.init().setDirectory(dir).setInitialBranch("main").call()

    private fun creds(token: String) = UsernamePasswordCredentialsProvider(token, "")

    private fun setRemote(g: Git, url: String) {
        val cfg = g.repository.config
        cfg.setString("remote", "origin", "url", url)
        cfg.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
        cfg.save()
    }

    /** add + commit + push: o git só envia o que mudou. */
    fun send(dir: File, url: String, token: String, msg: String): String =
        open(dir).use { g ->
            setRemote(g, url)
            g.add().addFilepattern(".").call()
            g.add().setUpdate(true).addFilepattern(".").call() // registra arquivos apagados
            val hasHead = g.repository.resolve("HEAD") != null
            if (hasHead && g.status().call().isClean) return "Nada mudou desde o último envio."
            g.commit().setMessage(msg).setAuthor(who).setCommitter(who).call()
            val results = g.push().setRemote("origin").add(g.repository.branch)
                .setCredentialsProvider(creds(token)).call()
            val bad = results.flatMap { it.remoteUpdates }.firstOrNull {
                it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE
            }
            if (bad != null) error(
                if (bad.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD)
                    "Envio recusado: a nuvem tem versões que você não tem. Use 'Baixar versão' primeiro."
                else "Envio recusado: ${bad.status} ${bad.message ?: ""}"
            )
            "Enviado: $msg"
        }

    /** fetch + reset hard: deixa a pasta igual à nuvem (descarta mudanças locais). */
    fun download(dir: File, url: String, token: String): String =
        open(dir).use { g ->
            setRemote(g, url)
            g.fetch().setRemote("origin").setCredentialsProvider(creds(token)).call()
            val repo = g.repository
            val branch = listOf("main", "master").firstOrNull { repo.resolve("refs/remotes/origin/$it") != null }
                ?: error("Não achei a branch main/master no repositório (ele está vazio?).")
            if (repo.resolve("HEAD") == null) {
                g.checkout().setCreateBranch(true).setName(branch)
                    .setStartPoint("origin/$branch").setForced(true).call()
            } else {
                g.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/$branch").call()
            }
            "Pasta atualizada com a versão da nuvem ($branch)."
        }
}
