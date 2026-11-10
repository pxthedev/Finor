import { Text, ScrollView } from "react-native";
export default function Privacy() {
  return (
    <ScrollView contentContainerStyle={{ padding: 24, gap: 20 }}>
      <Text style={{ color: "#fff", fontSize: 24 }}>Seus dados no Finor</Text>
      <Text style={{ color: "#A1A1A1", lineHeight: 24 }}>
        O Finor armazena seu nome, e-mail, registros financeiros e mensagens
        para organizar suas finanças. Não solicita senhas bancárias e não
        movimenta dinheiro.
      </Text>
      <Text style={{ color: "#A1A1A1", lineHeight: 24 }}>
        Ao usar o assistente, sua mensagem é enviada à GroqCloud para
        interpretação. Não inclua senhas, documentos ou informações de
        terceiros. Revise as propostas antes de confirmar.
      </Text>
      <Text style={{ color: "#A1A1A1", lineHeight: 24 }}>
        Você pode exportar seus registros e excluir sua conta na área Mais. A
        exclusão remove os dados do banco ativo e encerra suas sessões. Backups
        seguem a política de retenção do operador.
      </Text>
      <Text style={{ color: "#737373" }}>
        Versão de desenvolvimento. Os dados do controlador, fornecedores e
        canais de suporte precisam ser definidos antes da publicação.
      </Text>
    </ScrollView>
  );
}
