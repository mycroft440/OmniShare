# OmniShare 🚀

OmniShare é um aplicativo Android nativo desenvolvido em Kotlin e Jetpack Compose que permite compartilhar a conexão de internet do seu dispositivo (Wi-Fi ou Dados Móveis) através de uma rede Wi-Fi Direct e um Servidor Proxy local.

## ✨ Funcionalidades

- **Compartilhamento Flexível:** Compartilhe Wi-Fi conectado ou Dados Móveis (4G/5G).
- **Wi-Fi Direct (P2P):** Cria um hotspot independente que contorna limitações de tethering tradicionais.
- **Servidor Proxy Integrado:** Roteia o tráfego de rede de forma eficiente utilizando Sockets nativos.
- **Interface Moderna:** Desenvolvida inteiramente com Jetpack Compose.
- **Foreground Service:** Mantém o compartilhamento ativo em segundo plano.

## 🛠️ Como usar

1. **Inicie o App:** Abra o OmniShare e conceda as permissões de localização e dispositivos próximos.
2. **Ative o Compartilhamento:** Clique no botão "INICIAR COMPARTILHAMENTO".
3. **Conecte o Cliente:** No dispositivo que vai receber a internet, conecte-se à rede Wi-Fi exibida na tela do OmniShare.
4. **Configure o Proxy:**
   - Nas configurações avançadas da rede Wi-Fi conectada no dispositivo cliente, altere o **Proxy** para **Manual**.
   - **Host do Proxy:** `192.168.49.1`
   - **Porta do Proxy:** `8282`
5. **Pronto!** Agora o dispositivo cliente terá acesso à internet através do OmniShare.

## 🏗️ Arquitetura

O projeto utiliza:
- **Kotlin Coroutines:** Para processamento de rede assíncrono e eficiente.
- **Jetpack Compose:** Para uma interface de usuário reativa.
- **Android WifiP2pManager:** Para criação e gestão de grupos P2P.
- **ServerSocket/Socket:** Para a implementação do Proxy TCP/HTTPS.

## 📄 Licença

Este projeto é para fins educacionais e de demonstração.
