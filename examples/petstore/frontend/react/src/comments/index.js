import React, {Component} from 'react'
import Thread from "./Thread";
import AddComment from "./AddComment";
import config from "../config";
import {string} from 'prop-types'

class Comments extends Component {

  constructor() {
    super()

    this.state = {
      threads: [],
      reply: {
        poster: '',
        content: ''
      },
      enabled: false
    }
  }

  componentDidMount() {

    fetch(`${config.SERVER_URL}/comment/health`)
      .then(r => r.json())
      .then(json => {
        this.setState({enabled: json.status === 'UP'},
          () => this.state.enabled ? this.fetchThreads() : null)
      })
      .catch(e => console.warn(e))
  }

  fetchThreads = () => {
    const {topic} = this.props
    fetch(`${config.SERVER_URL}/comment/${topic}`)
      .then(r => r.json())
      .then(json => this.setState({threads: json, reply: {poster: '', content: ''}}))
      .catch(e => console.warn(e))
  }


  createThread = (e) => {
    e.preventDefault()

    const {topic} = this.props
    const thread = this.state.reply

    fetch(`${config.SERVER_URL}/comment/${topic}`, {
      method: 'POST',
      body: JSON.stringify(thread),
      headers: {'Content-Type': 'application/json'}
    }).then(r => {
      if (r.status === 201) {
        this.fetchThreads()
      } else {
        console.warn('Could not post thread')
      }
    })
      .catch(e => console.warn(e))

  }

  updateReply = (e) => {
    let {reply} = this.state
    reply[e.target.name] = e.target.value

    this.setState({reply})
  }

  addReply = (e, id) => {
    e.preventDefault()
    const {topic, reply} = this.state
    fetch(`${config.SERVER_URL}/comment/${topic}/${id}`, {
      method: 'POST',
      body: JSON.stringify(reply),
      headers: {'Content-Type': 'application/json'}
    }).then(r => {
      if (r.status === 201) {
        this.expandThread(id)
      } else {
        console.warn('Could not post reply')
      }
    })
  }

  expandThread = (id) => {
    const {topic} = this.props

    fetch(`${config.SERVER_URL}/comment/${topic}/${id}`)
      .then(r => r.json())
      .then(json => {
        console.log(json)
        let threads = this.state.threads

        threads = threads.map(t => {
          t.replies = (t.id === id) ? json.replies : null
          t.expanded = (t.id === id)
          return t
        })

        this.setState({threads, reply: {poster: '', content: ''}})
      })

  }

  closeThread = (id) => {
    let threads = this.state.threads.map(t => {
      if (t.id === id) {
        t.replies = null
        t.expanded = false
      }
      return t
    })
    this.setState({threads})
  }


  threadIsExpanded = () => !this.state.threads.find(t => t.expanded === true)

  expandForm = (e) => {
    e.preventDefault()
    let threads = this.state.threads.map(t => {
      t.replies = null
      t.expanded = false
      return t
    })
    this.setState({threads, reply: {poster: '', content: ''}})
  }

  render() {
    const {threads, reply, enabled} = this.state

    return enabled ? <div>
      <h2>Join the discussion!</h2>

      {threads.length > 0 ? threads.map(t => <Thread thread={t} key={t.id}
                                                     expand={() => this.expandThread(t.id)}
                                                     close={() => this.closeThread(t.id)}
                                                     reply={reply}
                                                     submitReply={(e) => this.addReply(e, t.id)}
                                                     updateReply={this.updateReply}/>) : null}


      <div className='card' style={{clear: 'both', marginTop: '20px'}}>
        <div className='card-header'><b>Start a new discussion!</b></div>
        <AddComment submit={this.createThread} update={this.updateReply} comment={reply}
                                        expand={this.expandForm}
                                        expanded={this.threadIsExpanded()}/></div>

    </div> : null
  }
}

Comments.propTypes = {
  topic: string.isRequired
}

export default Comments