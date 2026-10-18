# Schematic Ghost

Mod **Fabric** para **Minecraft 26.3**, **somente client side**, que mostra um schematic como "fantasma" dentro do jogo e indica quais blocos ainda faltam construir. Inspirado na ideia do Litematica, mas bem mais enxuto: você carrega o arquivo, posiciona e constrói seguindo o esqueleto.

![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-62b47a)
![Fabric](https://img.shields.io/badge/Mod%20loader-Fabric-dbd0b4)
![Client side](https://img.shields.io/badge/Lado-Cliente-blue)
![Licença MIT](https://img.shields.io/badge/Licen%C3%A7a-MIT-green)

> **Adicione aqui um print ou GIF do mod funcionando.** Uma imagem vale mais que qualquer descrição.
> Exemplo: `![Demonstração](docs/demo.png)`

---

## Recursos

- Lê schematics nos formatos **`.litematic`** (Litematica) e **`.schem`** (WorldEdit/Sponge, versões 2 e 3).
- **Blocos fantasmas translúcidos** com o modelo e a textura reais de cada bloco que falta, inclusive direção de escadas, portas etc.
- **Contornos coloridos**: azul onde falta um bloco, vermelho onde há um bloco diferente do esperado.
- **Painel dentro do jogo** (tecla `G`) para escolher o arquivo, ligar/desligar opções, ajustar opacidade e alcance e mover o schematic.
- Comandos `/ghost` para quem prefere o teclado.
- Contagem de blocos faltando e errados em tempo real, só na área perto de você.
- 100% visual: **não coloca blocos por você** e não envia nada ao servidor.

## Requisitos

| Item | Versão |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 ou superior |
| Fabric API | 0.163.0+26.3 (ou compatível com a 26.3) |
| Java | 25 |

## Instalação

1. Instale o [Fabric Loader](https://fabricmc.net/use/) para o Minecraft 26.3.
2. Baixe o [Fabric API](https://modrinth.com/mod/fabric-api) compatível com a 26.3 e coloque na pasta `mods`.
3. Baixe o `.jar` do Schematic Ghost na aba **Releases** deste repositório e coloque na pasta `mods`.
4. Crie a pasta `schematics` dentro da pasta do jogo (a mesma onde fica `mods`). O mod também cria essa pasta sozinho na primeira vez que você abre o painel ou usa `/ghost list`.
5. Coloque seus arquivos `.litematic` ou `.schem` dentro de `schematics`.

No Windows, a pasta do jogo normalmente é `%appdata%\.minecraft`.

## Como usar

### Pelo painel (recomendado)

1. Entre em um mundo e aperte **G** (a tecla pode ser trocada em *Opções > Controles > Schematic Ghost*).
2. Clique em um arquivo da lista. Na primeira vez, a origem do schematic fica **onde você está**.
3. Ajuste o que quiser:
   - **Ativo / Blocos fantasmas / Translúcido / Contornos**: liga e desliga cada parte.
   - **Opacidade** e **Alcance**: botões de mais e menos.
   - **Origem: onde estou**: move o schematic para a sua posição atual.
   - **X-, X+, Y-, Y+, Z-, Z+**: movem a origem 1 bloco por clique.
4. Feche com `ESC` e construa. Quando você coloca o bloco certo, o fantasma e o contorno desaparecem.

> O canto de menor coordenada do schematic fica na origem. Se a construção aparecer deslocada, use os botões de mover a origem.

### Pelos comandos

| Comando | O que faz |
|---|---|
| `/ghost list` | Lista os arquivos da pasta `schematics` |
| `/ghost load <arquivo>` | Carrega um schematic e mostra um resumo |
| `/ghost origin` | Define a origem na sua posição atual |
| `/ghost status` | Mostra quantos blocos faltam, quantos estão errados e quantos estão sendo desenhados |
| `/ghost compare` | Compara o schematic inteiro com o mundo (só chunks carregados) |
| `/ghost toggle` | Liga/desliga o mod |
| `/ghost blocks` | Liga/desliga os blocos fantasmas |
| `/ghost outline` | Liga/desliga os contornos |
| `/ghost translucent` | Alterna entre fantasma translúcido e sólido |
| `/ghost alpha <10-100>` | Opacidade dos fantasmas translúcidos (padrão 45) |
| `/ghost range <8-128>` | Alcance, em blocos, da área verificada ao redor de você (padrão 48) |

## Cores

| Aparência | Significado |
|---|---|
| Fantasma translúcido com contorno **azul** | Falta colocar este bloco |
| Contorno **vermelho** | Existe um bloco aí, mas é diferente do esperado |
| Nada | O bloco está correto |

## Limitações conhecidas

- **Sem rotação nem espelhamento** do schematic por enquanto. Só dá para mover.
- A comparação considera **apenas o tipo do bloco**. Uma escada virada para o lado errado conta como correta.
- Mostra no máximo **1000 blocos por vez**, os mais próximos de você. Se o `/ghost status` mostrar "desenhados 1000", ainda há mais blocos faltando mais longe.
- Só verifica **chunks carregados** e só dentro do alcance configurado.
- **Blocos com tinta de bioma** (grama, folhas, água) aparecem sem a cor do bioma no fantasma.
- **Blocos especiais** desenhados pelo jogo de outra forma (baús, camas, placas, cabeças) não aparecem como fantasma. Para eles só o contorno é mostrado.
- Não lê `.schematic` antigo do MCEdit nem `.nbt` de estruturas.
- Schematics muito grandes podem pesar. Diminua o alcance com `/ghost range`.

## Compilar a partir do código-fonte

Requer **JDK 25**.

```bash
git clone https://github.com/<seu-usuario>/schematic-ghost.git
cd schematic-ghost

# testar no jogo (abre o Minecraft de desenvolvimento)
./gradlew runClient

# gerar o .jar
./gradlew build
```

No Windows, use `gradlew.bat` (ou `gradlew` no CMD). O `.jar` fica em `build/libs`; use o que **não** termina em `-sources`. Na pasta de desenvolvimento, os schematics ficam em `run/schematics`.

> Dica: evite deixar o projeto dentro de pastas sincronizadas, como o OneDrive. O Gradle gera muitos arquivos e a sincronização pode causar erros.

## Estrutura do projeto

```
src/main/java/dev/schematicghost/
├── SchematicGhostClient.java      entrada do mod (client)
├── nbt/
│   └── Nbt.java                   leitor NBT simples, sem depender do Minecraft
├── schematic/
│   ├── LitematicSchematic.java    leitor de .litematic
│   ├── SpongeSchematic.java       leitor de .schem (v2 e v3)
│   └── FlatSchematic.java         lista achatada de blocos para varredura rápida
└── client/
    ├── SchematicManager.java      arquivo carregado e origem
    ├── GhostState.java            compara schematic x mundo e guarda o resultado
    ├── GhostRenderer.java         contornos e entrega dos fantasmas ao jogo
    ├── TranslucentGhost.java      desenho dos blocos com transparência
    ├── BlockResolver.java         converte o bloco do schematic em BlockState
    ├── GhostScreen.java           painel dentro do jogo
    ├── GhostKeys.java             tecla de atalho
    └── GhostCommands.java         comandos /ghost
```

Os leitores de arquivo (`nbt/` e `schematic/`) não usam nenhuma classe do Minecraft, então podem ser testados fora do jogo e sofrem pouco com mudanças de versão.

## Roadmap

- [ ] Lista de materiais (quantos blocos de cada tipo faltam)
- [ ] Rotação e espelhamento
- [ ] Comparar também a direção/propriedades dos blocos
- [ ] Cor de bioma nos fantasmas
- [ ] Ver os contornos através das paredes
- [ ] Suporte a `.nbt` de estruturas

## Contribuindo

Encontrou um bug ou tem uma ideia? Abra uma *issue*. Ao reportar um problema, inclua:

- a versão do Minecraft, do Fabric Loader e do Fabric API;
- o arquivo de log (`logs/latest.log`) ou o relatório de crash;
- o schematic que causou o problema, se puder compartilhar.

Pull requests são bem-vindos.

## Servidores e uso responsável

O mod é apenas visual e roda só no cliente. Mesmo assim, **cada servidor tem as próprias regras** sobre mods de cliente. Confira as regras antes de usar em um servidor multijogador.

## Licença

Distribuído sob a licença [MIT](LICENSE). Adicione o arquivo `LICENSE` ao repositório.

## Aviso

Este projeto é independente. Não é afiliado, aprovado ou mantido pela Mojang, pela Microsoft nem pelos autores do Litematica. *Minecraft* é uma marca da Mojang Synergies AB. Nenhum código do Litematica foi usado.
