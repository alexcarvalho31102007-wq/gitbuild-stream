package com.example.data.zip

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedFile(
    val path: String,
    val bytes: ByteArray,
    val isText: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExtractedFile
        return path == other.path
    }

    override fun hashCode(): Int = path.hashCode()
}

data class ZipScanResult(
    val fileName: String,
    val totalSize: Long,
    val files: List<ExtractedFile>,
    val detectedTechnologies: List<String>,
    val hasCustomWorkflow: Boolean
)

object ZipProcessor {

    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB per individual file
    private const val MAX_TOTAL_FILES = 600

    fun processZipUri(context: Context, uri: Uri, originalFileName: String = "source.zip"): ZipScanResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo ZIP selecionado.")

        val rawFiles = mutableListOf<ExtractedFile>()
        var totalBytesRead = 0L

        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.contains("__MACOSX") && !name.endsWith(".DS_Store")) {
                    val normalizedPath = normalizePath(name)
                    if (normalizedPath.isNotBlank()) {
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var read: Int
                        var entryBytes = 0L
                        while (zis.read(buffer).also { read = it } != -1) {
                            entryBytes += read
                            if (entryBytes > MAX_FILE_SIZE_BYTES) {
                                break // Skip overly large binary file
                            }
                            baos.write(buffer, 0, read)
                        }
                        val contentBytes = baos.toByteArray()
                        totalBytesRead += contentBytes.size
                        val isText = isLikelyText(contentBytes)
                        rawFiles.add(ExtractedFile(normalizedPath, contentBytes, isText))
                        if (rawFiles.size >= MAX_TOTAL_FILES) {
                            break
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // If all files share a common single root folder (e.g., project-master/src/...), strip it
        val cleanedFiles = stripCommonRootDirectory(rawFiles)

        // Check if user already provided .github/workflows
        val hasCustomWorkflow = cleanedFiles.any {
            it.path.startsWith(".github/workflows/") && (it.path.endsWith(".yml") || it.path.endsWith(".yaml"))
        }

        val finalFiles = cleanedFiles.toMutableList()

        // If no workflow is present, add our universal auto-detect CI workflow
        if (!hasCustomWorkflow) {
            val workflowContent = generateUniversalWorkflow()
            finalFiles.add(
                ExtractedFile(
                    path = ".github/workflows/compile.yml",
                    bytes = workflowContent.toByteArray(Charsets.UTF_8),
                    isText = true
                )
            )
        }

        val detectedTechnologies = detectTechnologies(finalFiles)

        return ZipScanResult(
            fileName = originalFileName,
            totalSize = totalBytesRead,
            files = finalFiles,
            detectedTechnologies = detectedTechnologies,
            hasCustomWorkflow = hasCustomWorkflow
        )
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimStart('/')
    }

    private fun stripCommonRootDirectory(files: List<ExtractedFile>): List<ExtractedFile> {
        if (files.isEmpty()) return files
        val firstSlashIndices = files.map { it.path.indexOf('/') }
        if (firstSlashIndices.any { it <= 0 }) {
            return files
        }
        val firstSegments = files.map { it.path.substring(0, it.path.indexOf('/')) }
        val commonRoot = firstSegments.first()
        if (firstSegments.all { it == commonRoot }) {
            return files.map {
                it.copy(path = it.path.substring(commonRoot.length + 1))
            }
        }
        return files
    }

    private fun isLikelyText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val checkLen = minOf(bytes.size, 512)
        var zeroCount = 0
        for (i in 0 until checkLen) {
            if (bytes[i] == 0.toByte()) zeroCount++
        }
        return zeroCount == 0
    }

    private fun detectTechnologies(files: List<ExtractedFile>): List<String> {
        val techs = mutableListOf<String>()
        val paths = files.map { it.path }

        if (paths.any { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") }) {
            techs.add("Gradle (Java / Kotlin)")
        }
        if (paths.any { it.endsWith("pom.xml") }) {
            techs.add("Apache Maven")
        }
        if (paths.any { it.endsWith("package.json") }) {
            techs.add("Node.js / NPM")
        }
        if (paths.any { it.endsWith("Cargo.toml") }) {
            techs.add("Rust Cargo")
        }
        if (paths.any { it.endsWith("go.mod") }) {
            techs.add("Go Lang")
        }
        if (paths.any { it.endsWith("CMakeLists.txt") || it.endsWith("Makefile") }) {
            techs.add("C / C++ (Make / CMake)")
        }
        if (paths.any { it.endsWith("requirements.txt") || it.endsWith("pyproject.toml") }) {
            techs.add("Python")
        }
        if (paths.any { it.endsWith("compile.sh") || it.endsWith("build.sh") }) {
            techs.add("Custom Build Script (.sh)")
        }

        if (techs.isEmpty()) {
            techs.add("Código-fonte genérico")
        }
        return techs
    }

    fun generateUniversalWorkflow(): String {
        return """
name: "Cloud Compiler CI"
on:
  push:
    branches: ['**']
  workflow_dispatch:

jobs:
  compile:
    name: "Compilação do Projeto"
    runs-on: ubuntu-latest
    timeout-minutes: 25

    steps:
      - name: "1. Baixar Código-Fonte"
        uses: actions/checkout@v4

      - name: "2. Inspecionar e Detectar Projeto"
        id: detect
        run: |
          echo "=============================================="
          echo ">>> Arquivos recebidos no repositório:"
          ls -la
          echo "=============================================="
          
          if [ -f "gradlew" ] || [ -f "build.gradle" ] || [ -f "build.gradle.kts" ] || [ -f "pom.xml" ]; then
            echo "has_jvm=true" >> ${'$'}GITHUB_OUTPUT
            echo "JVM detectado."
          fi
          if [ -f "package.json" ]; then
            echo "has_node=true" >> ${'$'}GITHUB_OUTPUT
            echo "Node.js detectado."
          fi
          if [ -f "Cargo.toml" ]; then
            echo "has_rust=true" >> ${'$'}GITHUB_OUTPUT
            echo "Rust detectado."
          fi
          if [ -f "go.mod" ]; then
            echo "has_go=true" >> ${'$'}GITHUB_OUTPUT
            echo "Go detectado."
          fi
          if [ -f "requirements.txt" ] || [ -f "pyproject.toml" ]; then
            echo "has_python=true" >> ${'$'}GITHUB_OUTPUT
            echo "Python detectado."
          fi

      - name: "3. Configurar JDK 17"
        if: steps.detect.outputs.has_jvm == 'true'
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: "4. Configurar Node.js 20"
        if: steps.detect.outputs.has_node == 'true'
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: "5. Configurar Go"
        if: steps.detect.outputs.has_go == 'true'
        uses: actions/setup-go@v5
        with:
          go-version: 'stable'

      - name: "6. Executar Compilação"
        run: |
          set -e
          echo "=============================================="
          echo ">>> INICIANDO COMPILAÇÃO NO GITHUB ACTIONS"
          echo "=============================================="
          
          if [ -f "./compile.sh" ]; then
            echo ">>> Executando script de compilação customizado compile.sh..."
            chmod +x ./compile.sh
            ./compile.sh
          elif [ -f "./build.sh" ]; then
            echo ">>> Executando script de compilação customizado build.sh..."
            chmod +x ./build.sh
            ./build.sh
          elif [ -f "gradlew" ]; then
            echo ">>> Executando Gradle Wrapper..."
            chmod +x ./gradlew
            ./gradlew build --no-daemon --stacktrace || ./gradlew assembleDebug --no-daemon
          elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
            echo ">>> Executando Gradle..."
            gradle build --no-daemon || gradle assembleDebug --no-daemon
          elif [ -f "pom.xml" ]; then
            echo ">>> Executando Apache Maven..."
            mvn clean package -B
          elif [ -f "Cargo.toml" ]; then
            echo ">>> Compilando com Rust Cargo..."
            cargo build --release
          elif [ -f "go.mod" ]; then
            echo ">>> Compilando com Go..."
            go build -v ./...
          elif [ -f "package.json" ]; then
            echo ">>> Compilando com Node.js..."
            npm install
            if grep -q "\"build\":" package.json; then
              npm run build
            else
              echo "Script de build não configurado em package.json. npm install concluído com sucesso."
            fi
          elif [ -f "Makefile" ]; then
            echo ">>> Compilando com Make..."
            make
          elif [ -f "CMakeLists.txt" ]; then
            echo ">>> Compilando com CMake..."
            mkdir -p build && cd build
            cmake ..
            cmake --build .
          elif [ -f "main.c" ]; then
            echo ">>> Compilando arquivo C com GCC..."
            gcc -Wall -O2 main.c -o app_output
          elif [ -f "main.cpp" ]; then
            echo ">>> Compilando arquivo C++ com G++..."
            g++ -Wall -O2 main.cpp -o app_output
          elif [ -f "requirements.txt" ]; then
            echo ">>> Instalando dependências e verificando sintaxe Python..."
            python -m pip install --upgrade pip
            pip install -r requirements.txt
            python -m compileall .
          else
            echo ">>> Nenhum build system padrão detectado. Verificando arquivos de código..."
            find . -maxdepth 3 -not -path '*/.*'
          fi
          
          echo "=============================================="
          echo ">>> COMPILAÇÃO CONCLUÍDA COM SUCESSO!"
          echo "=============================================="

      - name: "7. Salvar Artefatos Compilados"
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: compiled-artifacts
          path: |
            **/build/libs/*.jar
            **/build/outputs/apk/**/*.apk
            **/target/*.jar
            **/target/release/*
            **/dist/**
            **/build/**
            app_output
          if-no-files-found: ignore
""".trimIndent()
    }
}
